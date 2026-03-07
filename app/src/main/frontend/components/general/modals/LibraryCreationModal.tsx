import React, {useState} from "react";
import {
    addToast,
    Alert,
    Button,
    Checkbox,
    Link,
    Modal,
    ModalBody,
    ModalContent,
    ModalFooter,
    ModalHeader
} from "@heroui/react";
import {Form, Formik} from "formik";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import Input from "Frontend/components/general/input/Input";
import * as Yup from "yup";
import DirectoryMappingInput from "Frontend/components/general/input/DirectoryMappingInput";
import ArrayInputAutocomplete from "Frontend/components/general/input/ArrayInputAutocomplete";
import {useSnapshot} from "valtio/react";
import {platformState} from "Frontend/state/PlatformState";
import LibraryAdminDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryAdminDto";
import {pluginState, PluginType} from "Frontend/state/PluginState";
import PluginState from "Frontend/generated/org/pf4j/PluginState";

interface LibraryCreationModalProps {
    isOpen: boolean;
    onOpenChange: () => void;
}

export default function LibraryCreationModal({
                                                 isOpen,
                                                 onOpenChange
                                             }: LibraryCreationModalProps) {

    const [scanAfterCreation, setScanAfterCreation] = useState<boolean>(true);
    const availablePlatforms = useSnapshot(platformState).available;
    const metadataPlugins = useSnapshot(pluginState).sortedByType[PluginType.GameMetadataProvider].filter(p => p.state == PluginState.STARTED);
    const hasActiveMetadataPlugins = metadataPlugins && metadataPlugins.length > 0;

    async function createLibrary(library: LibraryAdminDto) {
        await LibraryEndpoint.createLibrary(library, scanAfterCreation);

        addToast({
            title: "New library created",
            description: `Library ${library.name} created!`,
            color: "success"
        });
    }

    return (availablePlatforms &&
        <>
            <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="opaque" size="xl">
                <ModalContent>
                    {(onClose) => (
                        <Formik
                            initialValues={{
                                name: "",
                                directories: [],
                                platforms: []
                            }}
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
                                            <ArrayInputAutocomplete options={Array.from(availablePlatforms)}
                                                                    name="platforms"
                                                                    label="Platforms"
                                                                    placeholder="Platform(s) of the games in this library (leave empty for all platforms)"
                                            />
                                            <DirectoryMappingInput name="directories"/>
                                        </div>
                                        {(!metadataPlugins || metadataPlugins.length == 0) &&
                                            <Alert color="warning">
                                                <p>No metadata plugins are currently enabled.</p>
                                                <p>Go to <Link underline="always" color="foreground"
                                                               href="/administration/plugins">Plugins</Link> and enable
                                                    at least one metadata plugin in order to scan your library.</p>
                                            </Alert>
                                        }
                                    </ModalBody>
                                    <ModalFooter className="flex flex-row justify-between">
                                        <Checkbox
                                            isSelected={hasActiveMetadataPlugins && scanAfterCreation}
                                            isDisabled={!hasActiveMetadataPlugins}
                                            onValueChange={setScanAfterCreation}
                                        >Scan after creation?</Checkbox>
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