import React from "react";
import {addToast, Button, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader} from "@heroui/react";
import {Form, Formik} from "formik";
import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/LibraryDto";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import Input from "Frontend/components/general/input/Input";

interface LibraryCreationModalProps {
    libraries: LibraryDto[];
    setLibraries: (libraries: LibraryDto[]) => void;
    isOpen: boolean;
    onOpenChange: () => void;
}

export default function LibraryCreationModal({
                                                 libraries,
                                                 setLibraries,
                                                 isOpen,
                                                 onOpenChange
                                             }: LibraryCreationModalProps) {
    async function createLibrary(library: LibraryDto) {
        try {
            const newLibrary = await LibraryEndpoint.createLibrary(library as LibraryDto);
            if (newLibrary === undefined) return;
            setLibraries([...libraries, newLibrary]);
        } catch (e) {
            addToast({
                title: "Error creating library",
                description: `Library ${library.name} could not be created!`,
                color: "warning"
            });
            return;
        }

        addToast({
            title: "New library created",
            description: `Library ${library.name} created!`,
            color: "success"
        });
    }

    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="opaque" size="lg">
            <ModalContent>
                {(onClose) => (
                    <Formik initialValues={{name: "", path: ""}}
                            onSubmit={async (values: any) => {
                                await createLibrary(values);
                                onClose();
                            }}
                    >
                        {(formik) => (
                            <Form>
                                <ModalHeader className="flex flex-col gap-1">Add a new library</ModalHeader>
                                <ModalBody>
                                    <h4 className="text-l font-bold">Details</h4>
                                    <div className="flex flex-col gap-2">
                                        <Input
                                            name="name"
                                            label="Library Name"
                                            placeholder="Enter library name"
                                            value={formik.values.name}
                                            required
                                        />
                                        <Input
                                            name="path"
                                            label="path"
                                            placeholder="Enter library path"
                                            value={formik.values.path}
                                            required
                                        />
                                    </div>
                                </ModalBody>
                                <ModalFooter>
                                    <Button variant="light" onPress={onClose}>
                                        Cancel
                                    </Button>
                                    <Button color="primary"
                                            isLoading={formik.isSubmitting}
                                            disabled={formik.isSubmitting}
                                            type="submit"
                                    >
                                        {formik.isSubmitting ? "" : "Add"}
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