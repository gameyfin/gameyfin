import React from "react";
import {Button, Modal, toast} from "@heroui/react";
import {Form, Formik} from "formik";
import Input from "Frontend/components/general/input/Input";
import {CollectionEndpoint} from "Frontend/generated/endpoints";
import CollectionCreateDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionCreateDto";
import * as Yup from "yup";
import TextAreaInput from "Frontend/components/general/input/TextAreaInput";

interface CollectionCreationModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
}

export default function CollectionCreationModal({
                                                    isOpen,
                                                    onOpenChange
                                                }: CollectionCreationModalProps) {

    async function createCollection(collection: CollectionCreateDto) {
        await CollectionEndpoint.createCollection(collection);

        toast.success("New collection created", {
            description: `Collection ${collection.name} created!`
        });
    }

    return (<>
            <Modal>
                <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
                    <Modal.Container size="lg" className="max-w-xl">
                        <Modal.Dialog>
                            {({close}) => (
                                <Formik
                                    initialValues={{
                                        name: "",
                                        description: ""
                                    }}
                                    validationSchema={Yup.object({
                                        name: Yup.string()
                                            .required("Collection name is required")
                                            .max(255, "Collection name must be 255 characters or less")
                                    })}
                                    isInitialValid={false}
                                    onSubmit={async (values: any) => {
                                        await createCollection(values);
                                        close();
                                    }}
                                >
                                    {(formik) =>
                                        <Form>
                                            <Modal.Header><Modal.Heading>Create a new collection</Modal.Heading></Modal.Header>
                                            <Modal.Body>
                                                <div className="flex flex-col gap-2">
                                                    <Input
                                                        name="name"
                                                        label="Collection Name"
                                                        placeholder="Enter collection name"
                                                        value={formik.values.name}
                                                        isRequired
                                                    />
                                                    <TextAreaInput
                                                        name="description"
                                                        label="Collection Description"
                                                        placeholder="Enter collection description"
                                                        value={formik.values.description}
                                                    />
                                                </div>
                                            </Modal.Body>
                                            <Modal.Footer className="flex flex-row justify-end">
                                                <Button variant="tertiary" onPress={close}>
                                                    Cancel
                                                </Button>
                                                <Button variant="primary"
                                                        isPending={formik.isSubmitting}
                                                        isDisabled={formik.isSubmitting}
                                                        type="submit"
                                                >
                                                    {formik.isSubmitting ? "" : "Add"}
                                                </Button>
                                            </Modal.Footer>
                                        </Form>
                                    }
                                </Formik>
                            )}
                        </Modal.Dialog>
                    </Modal.Container>
                </Modal.Backdrop>
            </Modal>
        </>
    );
}