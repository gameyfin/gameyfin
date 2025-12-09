import React from "react";
import {addToast, Button, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader} from "@heroui/react";
import {Form, Formik} from "formik";
import Input from "Frontend/components/general/input/Input";
import {CollectionEndpoint} from "Frontend/generated/endpoints";
import CollectionCreateDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionCreateDto";
import * as Yup from "yup";
import TextAreaInput from "Frontend/components/general/input/TextAreaInput";

interface CollectionCreationModalProps {
    isOpen: boolean;
    onOpenChange: () => void;
}

export default function CollectionCreationModal({
                                                    isOpen,
                                                    onOpenChange
                                                }: CollectionCreationModalProps) {

    async function createCollection(collection: CollectionCreateDto) {
        await CollectionEndpoint.createCollection(collection);

        addToast({
            title: "New collection created",
            description: `Collection ${collection.name} created!`,
            color: "success"
        });
    }

    return (<>
            <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="opaque" size="xl">
                <ModalContent>
                    {(onClose) => (
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
                                onClose();
                            }}
                        >
                            {(formik) =>
                                <Form>
                                    <ModalHeader className="flex flex-col gap-1">Create a new collection</ModalHeader>
                                    <ModalBody>
                                        <div className="flex flex-col gap-2">
                                            <Input
                                                name="name"
                                                label="Collection Name"
                                                placeholder="Enter collection name"
                                                value={formik.values.name}
                                                required
                                            />
                                            <TextAreaInput
                                                name="description"
                                                label="Collection Description"
                                                placeholder="Enter collection description"
                                                value={formik.values.description}
                                            />
                                        </div>
                                    </ModalBody>
                                    <ModalFooter className="flex flex-row justify-end">
                                        <Button variant="light" onPress={onClose}>
                                            Cancel
                                        </Button>
                                        <Button color="primary"
                                                isLoading={formik.isSubmitting}
                                                isDisabled={formik.isSubmitting}
                                                type="submit"
                                        >
                                            {formik.isSubmitting ? "" : "Add"}
                                        </Button>
                                    </ModalFooter>
                                </Form>
                            }
                        </Formik>
                    )}
                </ModalContent>
            </Modal>
        </>
    );
}