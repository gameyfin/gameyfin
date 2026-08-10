import React, {useState} from "react";
import {
    Alert,
    Button,
    Checkbox,
    Link,
    Modal,
    toast
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
import {pluginState} from "Frontend/state/PluginState";

interface LibraryCreationModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
}

export default function LibraryCreationModal({
                                                 isOpen,
                                                 onOpenChange
                                             }: LibraryCreationModalProps) {

    const [scanAfterCreation, setScanAfterCreation] = useState<boolean>(true);
    const availablePlatforms = useSnapshot(platformState).available;
    const hasActiveMetadataPlugins = useSnapshot(pluginState).hasActiveMetadataPlugins;

    async function createLibrary(library: LibraryAdminDto) {
        await LibraryEndpoint.createLibrary(library, hasActiveMetadataPlugins && scanAfterCreation);

        toast.success("New library created", {
            description: `Library ${library.name} created!`
        });
    }

    return (availablePlatforms &&
        <>
            <Modal>
                <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
                    <Modal.Container size="lg" className="max-w-xl">
                        <Modal.Dialog>
                            {({close}) => (
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
                                        close();
                                    }}
                                >
                                    {(formik) =>
                                        <Form>
                                            <Modal.Header><Modal.Heading>Add a new library</Modal.Heading></Modal.Header>
                                            <Modal.Body>
                                                <div className="flex flex-col gap-2">
                                                    <Input
                                                        name="name"
                                                        label="Library Name"
                                                        placeholder="Enter library name"
                                                        value={formik.values.name}
                                                        isRequired
                                                    />
                                                    <ArrayInputAutocomplete options={Array.from(availablePlatforms)}
                                                                            name="platforms"
                                                                            label="Platforms"
                                                                            placeholder="Platform(s) of the games in this library (leave empty for all platforms)"
                                                    />
                                                    <DirectoryMappingInput name="directories"/>
                                                </div>
                                                {!hasActiveMetadataPlugins &&
                                                    <Alert status="warning">
                                                        <Alert.Content>
                                                            <Alert.Description>
                                                                <p>No metadata plugins are currently enabled.</p>
                                                                <p>Go to <Link className="underline"
                                                                               href="/administration/plugins">Plugins</Link> and
                                                                    enable
                                                                    at least one metadata plugin in order to scan your library.</p>
                                                            </Alert.Description>
                                                        </Alert.Content>
                                                    </Alert>
                                                }
                                            </Modal.Body>
                                            <Modal.Footer className="flex flex-row justify-between">
                                                <Checkbox
                                                    isSelected={hasActiveMetadataPlugins && scanAfterCreation}
                                                    isDisabled={!hasActiveMetadataPlugins}
                                                    onChange={setScanAfterCreation}
                                                >
                                                    <Checkbox.Content>
                                                        <Checkbox.Control>
                                                            <Checkbox.Indicator/>
                                                        </Checkbox.Control>
                                                        Scan after creation?
                                                    </Checkbox.Content>
                                                </Checkbox>
                                                <div className="flex flex-row">
                                                    <Button variant="tertiary" onPress={close}>
                                                        Cancel
                                                    </Button>
                                                    <Button variant="primary"
                                                            isPending={formik.isSubmitting}
                                                            isDisabled={formik.isSubmitting}
                                                            type="submit"
                                                    >
                                                        {formik.isSubmitting ? "" : "Add"}
                                                    </Button>
                                                </div>
                                            </Modal.Footer>
                                        </Form>
                                    }
                                </Formik>
                            )}
                        </Modal.Dialog>
                    </Modal.Container>
                </Modal.Backdrop>
            </Modal>
        </>
    );
}