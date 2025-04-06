import {Button, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader} from "@heroui/react";
import {Form, Formik} from "formik";
import React, {useEffect, useState} from "react";
import Input from "Frontend/components/general/input/Input";
import FileTreeView from "Frontend/components/general/input/FileTreeView";

interface PathPickerModalProps {
    returnSelectedPath: (path: string) => void;
    isOpen: boolean;
    onOpenChange: () => void;
}

export default function PathPickerModal({returnSelectedPath, isOpen, onOpenChange}: PathPickerModalProps) {
    const [currentlySelectedPath, setCurrentlySelectedPath] = useState("");

    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="opaque" size="lg">
            <ModalContent>
                {(onClose) => (
                    <Formik initialValues={{path: currentlySelectedPath}}
                            onSubmit={(values: any) => {
                                returnSelectedPath(values.path);
                                onClose();
                            }}>
                        {(formik) => {
                            useEffect(() => {
                                formik.setFieldValue("path", currentlySelectedPath);
                            }, [currentlySelectedPath]);

                            return (
                                <Form>
                                    <ModalHeader className="flex flex-col gap-1">Add a new library</ModalHeader>
                                    <ModalBody>
                                        <Input
                                            name="path"
                                            label="Library Path"
                                            placeholder="&nbsp;"
                                            value={formik.values.path}
                                            isDisabled
                                            required
                                        />
                                        <div className="h-64 overflow-auto">
                                            <FileTreeView onPathChange={setCurrentlySelectedPath}/>
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
                                            {formik.isSubmitting ? "" : "Select"}
                                        </Button>
                                    </ModalFooter>
                                </Form>
                            );
                        }}
                    </Formik>
                )}
            </ModalContent>
        </Modal>
    )
}