import React from "react";
import {Button, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader} from "@heroui/react";
import {Form, Formik} from "formik";
import Input from "Frontend/components/general/input/Input";
import LibraryUpdateDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryUpdateDto";
import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryDto";
import Section from "Frontend/components/general/Section";

interface LibraryDetailsModalProps {
    library: LibraryDto;
    isOpen: boolean;
    onOpenChange: () => void;
    updateLibrary: (library: LibraryUpdateDto) => Promise<void>;
    removeLibrary: (library: LibraryDto) => Promise<void>;
}

export default function LibraryDetailsModal({
                                                library,
                                                isOpen,
                                                onOpenChange,
                                                updateLibrary,
                                                removeLibrary
                                            }: LibraryDetailsModalProps) {
    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="opaque" size="lg">
            <ModalContent>
                {(onClose) => {
                    async function update(values: LibraryUpdateDto) {
                        await updateLibrary(values);
                        onClose();
                    }

                    async function remove(library: LibraryDto) {
                        await removeLibrary(library);
                        onClose();
                    }

                    return (
                        <Formik initialValues={library}
                                enableReinitialize={true}
                                onSubmit={(values) => update(values)}
                        >
                            {(formik: { isSubmitting: any; }) => (
                                <Form>
                                    <ModalHeader className="flex flex-col gap-1">
                                        Edit library
                                    </ModalHeader>
                                    <ModalBody>
                                        <Input key="name" name="name" label="Name"/>

                                        <Section title="Danger zone"/>
                                        <Button onPress={() => remove(library)} color="danger">
                                            Delete this library
                                        </Button>
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
                    )
                }}
            </ModalContent>
        </Modal>
    );
}