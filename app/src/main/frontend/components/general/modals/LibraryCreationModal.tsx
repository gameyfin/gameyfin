import React, {useState} from "react";
import {addToast, Button, Checkbox, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader} from "@heroui/react";
import {Form, Formik} from "formik";
import LibraryDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryDto";
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
                                                 isOpen,
                                                 onOpenChange
                                             }: LibraryCreationModalProps) {

    const [scanAfterCreation, setScanAfterCreation] = useState<boolean>(true);

    async function createLibrary(library: LibraryDto) {
        try {
            await LibraryEndpoint.createLibrary(library as LibraryDto, scanAfterCreation);

            addToast({
                title: "New library created",
                description: `Library ${library.name} created!`,
                color: "success"
            });
        } catch (e) {
            addToast({
                title: "Error creating library",
                description: `Library ${library.name} could not be created!`,
                color: "warning"
            });
            throw "Error creating library: " + e;
        }
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
                                    <ModalFooter className="flex flex-row justify-between">
                                        <Checkbox isSelected={scanAfterCreation} onValueChange={setScanAfterCreation}>Scan
                                            after creation?</Checkbox>
                                        <div className="flex flex-row">
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
                                        </div>
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