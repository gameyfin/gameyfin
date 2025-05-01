import React from "react";
import {
    addToast,
    Button,
    Code,
    Modal,
    ModalBody,
    ModalContent,
    ModalFooter,
    ModalHeader,
    useDisclosure
} from "@heroui/react";
import {Form, Formik} from "formik";
import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryDto";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import Input from "Frontend/components/general/input/Input";
import PathPickerModal from "Frontend/components/general/modals/PathPickerModal";
import {Minus, Plus, XCircle} from "@phosphor-icons/react";
import * as Yup from "yup";
import {SmallInfoField} from "Frontend/components/general/SmallInfoField";

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
    const pathPickerModal = useDisclosure();

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
        <>
            <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="opaque" size="lg">
                <ModalContent>
                    {(onClose) => (
                        <Formik initialValues={{name: "", directories: []}}
                                validationSchema={Yup.object({
                                    name: Yup.string()
                                        .required("Library name is required")
                                        .max(255, "Library name must be 255 characters or less"),
                                    directories: Yup.array()
                                        .of(Yup.string())
                                        .min(1, "At least one directory is required")
                                })}
                                isInitialValid={false}
                                onSubmit={async (values: any) => {
                                    await createLibrary(values);
                                    onClose();
                                }}
                        >
                            {(formik) => {
                                function addDirectory(directory: string) {
                                    formik.setFieldValue("directories", [...formik.values.directories, directory]);
                                }

                                function removeDirectory(directory: string) {
                                    formik.setFieldValue("directories", formik.values.directories.filter((d: string) => d !== directory));
                                }

                                return (
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
                                                <div className="flex flex-row justify-between items-center">
                                                    <p className="font-bold">Directories</p>
                                                    <Button isIconOnly variant="light" size="sm" color="default"
                                                            onPress={pathPickerModal.onOpen}>
                                                        <Plus/>
                                                    </Button>
                                                </div>
                                                {formik.values.directories.map((directory: string) => (
                                                    <Code className="flex flex-row justify-between items-center">
                                                        {directory}
                                                        <Button isIconOnly variant="light" size="sm" color="default"
                                                                onPress={() => removeDirectory(directory)}>
                                                            <Minus/>
                                                        </Button>
                                                    </Code>
                                                ))}
                                                <div className="min-h-6 text-danger">
                                                    {(() => {
                                                        const meta = formik.getFieldMeta("directories");
                                                        return meta.touched && meta.error && (
                                                            <SmallInfoField icon={XCircle} message={meta.error}/>
                                                        );
                                                    })()}
                                                </div>
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
                                        <PathPickerModal returnSelectedPath={addDirectory}
                                                         isOpen={pathPickerModal.isOpen}
                                                         onOpenChange={pathPickerModal.onOpenChange}/>
                                    </Form>
                                );
                            }}
                        </Formik>
                    )}
                </ModalContent>
            </Modal>
        </>
    );
}