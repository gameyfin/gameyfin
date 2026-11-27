import {Button, Card, Spinner, toast, useOverlayState} from "@heroui/react";
import {useNavigate, useParams} from "react-router";
import React, {useEffect} from "react";
import {ArrowLeftIcon, TrashIcon} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import CollectionDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionDto";
import {collectionState} from "Frontend/state/CollectionState";
import {Form, Formik} from "formik";
import * as Yup from "yup";
import Input from "Frontend/components/general/input/Input";
import {deepDiff} from "Frontend/util/utils";
import {CollectionEndpoint} from "Frontend/generated/endpoints";
import CollectionUpdateDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionUpdateDto";
import TextAreaInput from "Frontend/components/general/input/TextAreaInput";
import CollectionHeader from "Frontend/components/general/covers/CollectionHeader";
import CollectionGamesTable from "Frontend/components/general/modals/CollectionGamesTable";
import CheckboxInput from "Frontend/components/general/input/CheckboxInput";
import ConfirmModal from "Frontend/components/general/modals/ConfirmModal";
import CollectionKind from "Frontend/generated/org/gameyfin/app/collections/entities/CollectionKind";
import {useAuth} from "Frontend/util/auth";
import {trackRun} from "Frontend/state/TaskState";
import SmartCollectionSection from "Frontend/components/general/collections/SmartCollectionSection";
import CollectionVisibilitySelect from "Frontend/components/general/collections/CollectionVisibilitySelect";

export default function CollectionManagementView({owned = false}: { owned?: boolean }) {
    const {collectionId} = useParams();
    const navigate = useNavigate();
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
        try {
            if (owned) await CollectionEndpoint.updateOwnedCollection(changed);
            else await CollectionEndpoint.updateCollection(changed);
            toast.success("Collection changes saved");
        } catch {
            toast.danger("Could not save collection changes");
        }
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
        } catch {
            toast.warning("Error deleting collection", {
                description: `Collection ${current.name} could not be deleted!`,
            });
        } finally {
            setIsDeleting(false);
        }
    }

    return (
        <div className="mx-auto flex w-full max-w-6xl flex-col gap-5">
            {!owned && <div className="flex items-center gap-3">
                <Button isIconOnly variant="ghost" aria-label="Back to collections" onPress={() => navigate(back)}>
                    <ArrowLeftIcon/>
                </Button>
                <h1 className="text-2xl font-bold">Manage collection</h1>
            </div>}
            <CollectionHeader collection={current} className={`h-24 sm:h-32 ${owned && "-mt-4"}`}/>

            <Formik
                initialValues={initialValues}
                onSubmit={handleSubmit}
                enableReinitialize
                validationSchema={Yup.object({
                    name: Yup.string()
                        .required("Collection name is required")
                        .max(255, "Collection name must be 255 characters or less")
                })}
            >
                {formik => (
                    <Form className="flex flex-col gap-4">
                        <div className={favourites ? "" : "grid gap-5 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]"}>
                            {!favourites && (
                                <Card className="min-w-0 gap-4 rounded-xl p-4 sm:p-6">
                                    <h2 className="text-lg font-semibold">Collection details</h2>
                                    <Input label="Name" name="name" className="min-h-0 lg:grow-0"/>
                                    <TextAreaInput label="Description" name="description" className="min-h-0 lg:grow"
                                                   textareaProps={{className: "lg:grow"}}/>
                                </Card>
                            )}
                            <div className="flex min-w-0 flex-col gap-5">
                                {!favourites && <SmartCollectionSection key={current.id} collectionId={current.id}
                                                                        enabled={current.smartEnabled}/>}
                                <Card className="gap-4 rounded-xl p-4 sm:p-6 lg:grow">
                                    <h2 className="text-lg font-semibold">Display settings</h2>
                                    <CollectionVisibilitySelect value={formik.values.visibility}
                                                                onChange={value => formik.setFieldValue("visibility", value)}/>
                                    {!owned &&
                                        <CheckboxInput label="Display on homepage" name="metadata.displayOnHomepage"/>}
                                </Card>
                            </div>
                        </div>
                        <div className="flex justify-end">
                            <Button type="submit" variant="primary" isPending={formik.isSubmitting}
                                    isDisabled={!formik.dirty}>
                                {formik.isSubmitting && <Spinner size="sm" color="current"/>}
                                Save changes
                            </Button>
                        </div>
                    </Form>
                )}
            </Formik>

            {!favourites && <>
                <CollectionGamesTable key={current.id} collectionId={current.id} owned={owned}/>
                <div className="flex justify-end">
                    <Button variant="danger-soft" onPress={deleteModal.open}>
                        <TrashIcon aria-hidden="true"/> Delete collection
                    </Button>
                </div>
                <ConfirmModal isOpen={deleteModal.isOpen}
                              onOpenChange={deleteModal.setOpen}
                              title="Delete collection"
                              confirmLabel="Delete"
                              isPending={isDeleting}
                              onConfirm={deleteCollection}>
                    <p>Delete <strong>{current.name}</strong>? This can’t be undone.</p>
                </ConfirmModal>
            </>}
        </div>
    );
}
