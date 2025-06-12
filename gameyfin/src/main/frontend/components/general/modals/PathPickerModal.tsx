import {Button, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader} from "@heroui/react";
import {Form, Formik} from "formik";
import React, {useEffect, useState} from "react";
import Input from "Frontend/components/general/input/Input";
import FileTreeView from "Frontend/components/general/input/FileTreeView";
import DirectoryMappingDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/DirectoryMappingDto";
import {ArrowRight} from "@phosphor-icons/react";

interface PathPickerModalProps {
    returnSelectedPath: (path: DirectoryMappingDto) => void;
    isOpen: boolean;
    onOpenChange: () => void;
}

export default function PathPickerModal({returnSelectedPath, isOpen, onOpenChange}: PathPickerModalProps) {
    const [internalPath, setInternalPath] = useState("");
    const [externalPath, setExternalPath] = useState("");

    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="opaque" size="3xl">
            <ModalContent>
                {(onClose) => (
                    <Formik initialValues={{internalPath: internalPath, externalPath: externalPath}}
                            onSubmit={(values: DirectoryMappingDto) => {
                                returnSelectedPath(values);
                                setInternalPath("");
                                setExternalPath("");
                                onClose();
                            }}>
                        {(formik) => {
                            useEffect(() => {
                                formik.setFieldValue("internalPath", internalPath);
                            }, [internalPath]);

                            return (
                                <Form>
                                    <ModalHeader className="flex flex-col gap-1">Select a folder</ModalHeader>
                                    <ModalBody>
                                        <div className="flex flex-row gap-2 items-center">
                                            <Input
                                                name="internalPath"
                                                label="Selected directory"
                                                placeholder="&nbsp;"
                                                value={formik.values.internalPath}
                                                isDisabled
                                                required
                                            />
                                            <ArrowRight className="mb-8"/>
                                            <Input
                                                name="externalPath"
                                                label="External path (optional)"
                                                placeholder="&nbsp;"
                                                value={formik.values.externalPath}
                                            />
                                        </div>
                                        <div className="h-64 overflow-auto">
                                            <FileTreeView onPathChange={setInternalPath}/>
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