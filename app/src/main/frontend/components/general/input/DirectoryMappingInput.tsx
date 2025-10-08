import React from "react";
import {Button, Code, useDisclosure} from "@heroui/react";
import {ArrowRight, Minus, Plus, XCircle} from "@phosphor-icons/react";
import PathPickerModal from "Frontend/components/general/modals/PathPickerModal";
import {SmallInfoField} from "Frontend/components/general/SmallInfoField";
import DirectoryMappingDto from "Frontend/generated/org/gameyfin/app/libraries/dto/DirectoryMappingDto";
import {useField} from "formik";

interface DirectoryMappingInputProps {
    name: string;
}

export default function DirectoryMappingInput({name}: DirectoryMappingInputProps) {
    const pathPickerModal = useDisclosure();
    const [field, meta, helpers] = useField<DirectoryMappingDto[]>({name});

    function addDirectoryMapping(directory: DirectoryMappingDto) {
        helpers.setValue([...(field.value || []), directory]);
    }

    function removeDirectoryMapping(directory: DirectoryMappingDto) {
        helpers.setValue((field.value || []).filter((d) => d !== directory));
    }

    return (
        <div className="flex flex-col gap-2">
            <div className="flex flex-row justify-between items-center">
                <p className="font-bold">Directories</p>
                <Button isIconOnly variant="light" size="sm" color="default"
                        onPress={pathPickerModal.onOpen}>
                    <Plus/>
                </Button>
            </div>
            {(field.value || []).map((directory) => (
                <Code
                    className="w-full flex items-center gap-2 overflow-hidden px-2 py-1"
                    key={directory.internalPath}>
                    <input
                        type="text"
                        value={directory.internalPath}
                        readOnly
                        className="flex-1 bg-transparent border-none outline-none overflow-x-auto whitespace-nowrap"
                    />
                    {directory.externalPath && (
                        <>
                            <div className="shrink-0 flex items-center justify-center mx-2">
                                <ArrowRight size={20}/>
                            </div>
                            <input
                                type="text"
                                value={directory.externalPath}
                                readOnly
                                className="flex-1 bg-transparent border-none outline-none overflow-x-auto whitespace-nowrap"
                            />
                        </>
                    )}
                    <Button
                        isIconOnly
                        variant="light"
                        size="sm"
                        color="default"
                        onPress={() => removeDirectoryMapping(directory)}
                        className="ml-2"
                    >
                        <Minus/>
                    </Button>
                </Code>
            ))}
            <div className="min-h-6 text-danger">
                {meta.touched && meta.error && (
                    <SmallInfoField icon={XCircle} message={meta.error}/>
                )}
            </div>
            <PathPickerModal returnSelectedPath={addDirectoryMapping}
                             isOpen={pathPickerModal.isOpen}
                             onOpenChange={pathPickerModal.onOpenChange}/>
        </div>
    );
}