import React from "react";
import {Button, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader} from "@heroui/react";
import {Form, Formik} from "formik";
import Input from "Frontend/components/general/input/Input";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import LibraryUpdateDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryUpdateDto";
import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryDto";

interface LibraryDetailsModalProps {
    library: LibraryDto;
    isOpen: boolean;
    onOpenChange: () => void;
    updateLibrary: (library: LibraryUpdateDto) => void;
}

export default function LibraryDetailsModal({library, isOpen, onOpenChange, updateLibrary}: LibraryDetailsModalProps) {
    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="opaque" size="lg">
            <ModalContent>
                {(onClose) => (
                    <Formik initialValues={library}
                            enableReinitialize={true}
                            onSubmit={async (values: LibraryUpdateDto) => {
                                updateLibrary(values);
                                onClose();
                            }}
                    >
                        {(formik: { isSubmitting: any; }) => (
                            <Form>
                                <ModalHeader className="flex flex-col gap-1">
                                    Edit library
                                </ModalHeader>
                                <ModalBody>
                                    <Input key="name" name="name" label="Name"/>
                                    <Button onPress={() => LibraryEndpoint.removeLibrary(library.id)}
                                            color="danger">Delete</Button>
                                </ModalBody>
                                <ModalFooter>
                                    <Button variant="light" onPress={onClose}>
                                        Cancel
                                    </Button>
                                    <Button
                                        color="primary"
                                        isLoading={formik.isSubmitting}
                                        disabled={formik.isSubmitting}
                                        type="submit"
                                    >
                                        {formik.isSubmitting ? "" : "Save"}
                                    </Button>
                                </ModalFooter>
                            </Form>
                        )}
                    </Formik>
                )}
            </ModalContent>
        </Modal>
    );
}