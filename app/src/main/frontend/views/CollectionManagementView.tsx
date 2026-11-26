import {Button, Label, ListBox, Select, Spinner, toast, useOverlayState} from "@heroui/react";
import {useNavigate, useParams} from "react-router";
import React, {useEffect} from "react";
import {ArrowLeftIcon, CheckIcon} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import CollectionDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionDto";
import {collectionState} from "Frontend/state/CollectionState";
import {Form, Formik} from "formik";
import * as Yup from "yup";
import Input from "Frontend/components/general/input/Input";
import Section from "Frontend/components/general/Section";
import {deepDiff} from "Frontend/util/utils";
import {CollectionEndpoint} from "Frontend/generated/endpoints";
import CollectionUpdateDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionUpdateDto";
import TextAreaInput from "Frontend/components/general/input/TextAreaInput";
import CollectionHeader from "Frontend/components/general/covers/CollectionHeader";
import CollectionGamesTable from "Frontend/components/general/modals/CollectionGamesTable";
import CheckboxInput from "Frontend/components/general/input/CheckboxInput";
import ConfirmModal from "Frontend/components/general/modals/ConfirmModal";
import CollectionKind from "Frontend/generated/org/gameyfin/app/collections/entities/CollectionKind";
import CollectionVisibility from "Frontend/generated/org/gameyfin/app/collections/entities/CollectionVisibility";
import {useAuth} from "Frontend/util/auth";
import {trackRun} from "Frontend/state/TaskState";

const visibilityOptions = [
    {id: CollectionVisibility.PRIVATE, label: "Private"},
    {id: CollectionVisibility.MEMBERS_ONLY, label: "Members only"},
    {id: CollectionVisibility.PUBLIC, label: "Public"}
];

export default function CollectionManagementView({owned = false}: {owned?: boolean}) {
    const {collectionId} = useParams();
    const navigate = useNavigate();
    const [collectionSaved, setCollectionSaved] = React.useState(false);
    const [isDeleting, setIsDeleting] = React.useState(false);
    const deleteModal = useOverlayState();
    const collections = useSnapshot(collectionState);
    const memberId = useAuth().state.user?.id;
    const back = owned ? "/collections" : "/administration/games";

    const collectionIdNum = Number(collectionId);

    const collection = collections.state[collectionIdNum] as CollectionDto | undefined;
    const canEdit = collection && (owned
        ? collection.ownerId != null && collection.ownerId === memberId
        : collection.kind === CollectionKind.CURATED);

    useEffect(() => {
        if (collections.isLoaded && !canEdit) navigate(back, {replace: true});
    }, [collections.isLoaded, canEdit, back, navigate]);

    if (!collection || !collectionIdNum || !canEdit) return null;
    const current = collection;
    const favourites = current.kind === CollectionKind.FAVOURITES;
    const initialValues: CollectionUpdateDto = {
        id: current.id,
        name: current.name,
        description: current.description ?? "",
        visibility: current.visibility,
        metadata: owned ? undefined : {displayOnHomepage: current.metadata.displayOnHomepage}
    };

    async function handleSubmit(values: CollectionUpdateDto): Promise<void> {
        const changed = deepDiff(initialValues, values) as CollectionUpdateDto;

        if (Object.keys(changed).length === 0) return;

        changed.id = current.id;
        if (owned) await CollectionEndpoint.updateOwnedCollection(changed);
        else await CollectionEndpoint.updateCollection(changed);
        setCollectionSaved(true);
        setTimeout(() => setCollectionSaved(false), 2000);
    }

    async function deleteCollection(): Promise<void> {
        setIsDeleting(true);
        try {
            if (owned) {
                await CollectionEndpoint.deleteOwnedCollection(current.id);
                toast.success("Collection deleted");
            } else {
                const run = await CollectionEndpoint.deleteCollection(current.id);
                trackRun(run);
                toast.info("Collection deletion started", {
                    description: "Progress is shown in the active tasks popover.",
                });
            }
            deleteModal.close();
            navigate(back);
        } catch (e) {
            toast.warning("Error deleting collection", {
                description: `Collection ${current.name} could not be deleted!`,
            });
        } finally {
            setIsDeleting(false);
        }
    }

    return (
        <div className="flex flex-col gap-4">
            <div className="flex flex-row gap-4 items-center">
                <Button isIconOnly variant="ghost" aria-label="Back to collections" onPress={() => navigate(back)}>
                    <ArrowLeftIcon/>
                </Button>
                <h1 className="text-2xl font-bold">Manage Collection</h1>
            </div>
            <CollectionHeader collection={current} className="h-32"/>
            <Formik
                initialValues={initialValues}
                onSubmit={handleSubmit}
                enableReinitialize={true}
                validationSchema={Yup.object({
                    name: Yup.string()
                        .required("Collection name is required")
                        .max(255, "Collection name must be 255 characters or less")
                })}
            >
                {(formik) => (
                    <Form>
                        <div className="flex flex-wrap gap-3 grow justify-between mb-4">
                            <h1 className="text-2xl font-bold">Edit collection details</h1>
                            <Button
                                variant="primary"
                                isPending={formik.isSubmitting}
                                isDisabled={formik.isSubmitting || collectionSaved || !formik.dirty}
                                type="submit"
                            >
                                {formik.isSubmitting ? <Spinner size="sm" color="current"/> : collectionSaved ?
                                    <CheckIcon/> : "Save"}
                            </Button>
                        </div>

                        {!favourites && <>
                            <Input label="Collection name" name="name"/>
                            <TextAreaInput label="Collection description" name="description"/>
                        </>}
                        <Select className="mb-4 max-w-64" value={formik.values.visibility}
                                onChange={value => value != null && formik.setFieldValue("visibility", value)}>
                            <Label>Visibility</Label>
                            <Select.Trigger><Select.Value/><Select.Indicator/></Select.Trigger>
                            <Select.Popover>
                                <ListBox items={visibilityOptions}>
                                    {option => <ListBox.Item id={option.id} textValue={option.label}>
                                        {option.label}<ListBox.ItemIndicator/>
                                    </ListBox.Item>}
                                </ListBox>
                            </Select.Popover>
                        </Select>
                        {!owned && <CheckboxInput label="Display on homepage" name="metadata.displayOnHomepage" className="mb-4"/>}

                        {!favourites && <div className="flex flex-col gap-4">
                            <h2 className="text-2xl font-bold">Manage games in collection</h2>
                            <CollectionGamesTable collectionId={collectionIdNum} owned={owned}/>
                        </div>}

                        {!favourites && <>
                            <Section title="Danger zone"/>
                            <Button variant="danger" onPress={deleteModal.open}>
                                Delete collection
                            </Button>
                            <ConfirmModal isOpen={deleteModal.isOpen}
                                          onOpenChange={deleteModal.setOpen}
                                          title="Delete collection"
                                          confirmLabel="Delete"
                                          isPending={isDeleting}
                                          onConfirm={deleteCollection}>
                                <p>Delete collection <code>{current.name}</code>? This can not be undone.</p>
                            </ConfirmModal>
                        </>}
                    </Form>
                )}
            </Formik>
        </div>
    );
}
