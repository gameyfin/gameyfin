import {Button, Modal} from "@heroui/react";
import {Form, Formik} from "formik";
import React, {useEffect, useState} from "react";
import Input from "Frontend/components/general/input/Input";
import FileTreeView from "Frontend/components/general/input/FileTreeView";
import DirectoryMappingDto from "Frontend/generated/org/gameyfin/app/libraries/dto/DirectoryMappingDto";
import { ArrowRightIcon } from "@phosphor-icons/react";

interface PathPickerModalProps {
    returnSelectedPath: (path: DirectoryMappingDto) => void;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
}

export default function PathPickerModal({returnSelectedPath, isOpen, onOpenChange}: PathPickerModalProps) {
    const [internalPath, setInternalPath] = useState("");
    const [externalPath, setExternalPath] = useState("");

    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} variant="opaque">
                <Modal.Container size="lg" className="max-w-3xl">
                    <Modal.Dialog>
                        {({close}) => (
                            <>
                                <Modal.CloseTrigger/>
                                <Formik initialValues={{internalPath: internalPath, externalPath: externalPath}}
                                        onSubmit={(values: DirectoryMappingDto) => {
                                            returnSelectedPath(values);
                                            setInternalPath("");
                                            setExternalPath("");
                                            close();
                                        }}>
                                    {(formik) => {
                                        useEffect(() => {
                                            formik.setFieldValue("internalPath", internalPath);
                                        }, [internalPath]);

                                        return (
                                            <Form>
                                                <Modal.Header className="flex flex-col gap-1">
                                                    <Modal.Heading>Select a folder</Modal.Heading>
                                                </Modal.Header>
                                                <Modal.Body>
                                                    <div className="flex flex-row gap-2 items-center">
                                                        <Input
                                                            name="internalPath"
                                                            label="Selected directory"
                                                            placeholder="&nbsp;"
                                                            value={formik.values.internalPath}
                                                            isDisabled
                                                            isRequired
                                                        />
                                                        <ArrowRightIcon className="mb-8"/>
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
                                                </Modal.Body>
                                                <Modal.Footer>
                                                    <Button variant="tertiary" onPress={close}>
                                                        Cancel
                                                    </Button>
                                                    <Button
                                                        variant="primary"
                                                        isPending={formik.isSubmitting}
                                                        isDisabled={formik.isSubmitting}
                                                        type="submit"
                                                    >
                                                        {formik.isSubmitting ? "" : "Select"}
                                                    </Button>
                                                </Modal.Footer>
                                            </Form>
                                        );
                                    }}
                                </Formik>
                            </>
                        )}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    )
}