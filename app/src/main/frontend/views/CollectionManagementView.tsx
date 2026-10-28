import {Button, toast, Spinner, useOverlayState} from "@heroui/react";
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
import {trackRun} from "Frontend/state/TaskState";

export default function CollectionManagementView() {
    const {collectionId} = useParams();
    const navigate = useNavigate();
    const [collectionSaved, setCollectionSaved] = React.useState(false);
    const [isDeleting, setIsDeleting] = React.useState(false);
    const deleteModal = useOverlayState();
    const collections = useSnapshot(collectionState);

    const collectionIdNum = collectionId ? parseInt(collectionId) : null;

    useEffect(() => {
        if (!collectionIdNum || (collections.isLoaded && !collections.state[collectionIdNum])) {
            navigate("/administration/games");
        }
    }, [collections, collectionIdNum, navigate]);

    if (!collectionIdNum) {
        return null;
    }

    const collection = collections.state[collectionIdNum] as CollectionDto;

    async function handleSubmit(values: CollectionUpdateDto): Promise<void> {
        const changed = deepDiff(collection, values) as CollectionUpdateDto;

        if (Object.keys(changed).length === 0) return;

        changed.id = collection.id;
        await CollectionEndpoint.updateCollection(changed);
        setCollectionSaved(true);
        setTimeout(() => setCollectionSaved(false), 2000);
    }

    async function deleteCollection(): Promise<void> {
        setIsDeleting(true);
        try {
            const run = await CollectionEndpoint.deleteCollection(collection.id);
            trackRun(run);

            toast.info("Collection deletion started", {
                description: "Progress is shown in the active tasks popover.",
            });

            deleteModal.close();
            navigate("/administration/games");
        } catch (e) {
            toast.warning("Error deleting collection", {
                description: `Collection ${collection.name} could not be deleted!`,
            });
        } finally {
            setIsDeleting(false);
        }
    }

    return collection && (
        <div className="flex flex-col gap-4">
            <div className="flex flex-row gap-4 items-center">
                <Button isIconOnly variant="ghost" onPress={() => history.back()}>
                    <ArrowLeftIcon/>
                </Button>
                <h1 className="text-2xl font-bold">Manage Collection</h1>
            </div>
            <CollectionHeader collection={collection} className="h-32"/>
            <Formik
                initialValues={collection}
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
                        <div className="flex flex-row grow justify-between mb-4">
                            <h1 className="text-2xl font-bold">Edit collection details</h1>
                            <Button
                                variant="primary"
                                isPending={formik.isSubmitting}
                                isDisabled={formik.isSubmitting || collectionSaved || !formik.dirty}
                                type="submit"
                            >
                                {formik.isSubmitting ? <Spinner size="sm" color="current"/> : collectionSaved ? <CheckIcon/> : "Save"}
                            </Button>
                        </div>

                        <Input label="Collection name" name="name"/>
                        <TextAreaInput label="Collection description" name="description"/>
                        <CheckboxInput label="Display on homepage" name="metadata.displayOnHomepage" className="mb-4"/>

                        <div className="flex flex-col gap-4">
                            <h1 className="text-2xl font-bold">Manage games in collection</h1>
                            <CollectionGamesTable collectionId={collectionIdNum}/>
                        </div>

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
                            <p>Delete collection <code>{collection.name}</code>? This can not be undone.</p>
                        </ConfirmModal>
                    </Form>
                )}
            </Formik>
        </div>
    );
}
