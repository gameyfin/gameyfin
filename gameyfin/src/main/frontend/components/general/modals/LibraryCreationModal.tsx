import React from "react";
import {addToast, Button, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader} from "@heroui/react";
import {Form, Formik} from "formik";
import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryDto";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import Input from "Frontend/components/general/input/Input";
import * as Yup from "yup";
import DirectoryMappingInput from "Frontend/components/general/input/DirectoryMappingInput";

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
        <>
            <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="opaque" size="xl">
                <ModalContent>
                    {(onClose) => (
                        <Formik initialValues={{name: "", directories: []}}
                                validationSchema={Yup.object({
                                    name: Yup.string()
                                        .required("Library name is required")
                                        .max(255, "Library name must be 255 characters or less"),
                                    directories: Yup.array()
                                        .of(Yup.object())
                                        .min(1, "At least one directory is required")
                                })}
                                isInitialValid={false}
                                onSubmit={async (values: any) => {
                                    await createLibrary(values);
                                    onClose();
                                }}
                        >
                            {(formik) =>
                                <Form>
                                    <ModalHeader className="flex flex-col gap-1">Add a new library</ModalHeader>
                                    <ModalBody>
                                        <div className="flex flex-col gap-2">
                                            <Input
                                                name="name"
                                                label="Library Name"
                                                placeholder="Enter library name"
                                                value={formik.values.name}
                                                required
                                            />
                                            <DirectoryMappingInput name="directories"/>
                                        </div>
                                    </ModalBody>
                                    <ModalFooter>
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