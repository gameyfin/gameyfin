import {Accordion, Button, Modal, Spinner} from "@heroui/react";
import {FastField, FieldProps, Form, Formik, FormikProvider} from "formik";
import Input from "Frontend/components/general/input/Input";
import React, {useEffect, useState} from "react";
import GameEnumPropertyValuesDto from "Frontend/generated/org/gameyfin/app/games/dto/GameEnumPropertyValuesDto";
import {GameEndpoint} from "Frontend/generated/endpoints";
import TextAreaInput from "Frontend/components/general/input/TextAreaInput";
import * as Yup from "yup";
import DatePickerInput from "Frontend/components/general/input/DatePickerInput";
import ArrayInput from "Frontend/components/general/input/ArrayInput";
import GameMediaEditor from "Frontend/components/general/input/GameMediaEditor";
import {GameMetadataFormValues, ManualGameImport, metadataInitialValues, metadataUpdate, saveMetadata} from "Frontend/util/gameMedia";
import AutocompleteInput from "Frontend/components/general/input/AutocompleteInput.tsx";
import {useSnapshot} from "valtio/react";
import {platformState} from "Frontend/state/PlatformState";
import {useFullGame} from "Frontend/state/GameState";
import {fileNameFromPath} from "Frontend/util/utils";

type EditGameMetadataModalProps = ({gameId: number; newGame?: never} | {gameId?: never; newGame: ManualGameImport}) & {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
}

function MetadataField({name, children}: {name: string; children: React.ReactNode}) {
    // Existing input components use useField. Scope their context to this FastField so
    // unrelated edits do not rerender every control in the metadata form.
    return <FastField name={name}>
        {({form}: FieldProps<unknown, GameMetadataFormValues>) =>
            <FormikProvider value={form}>{children}</FormikProvider>}
    </FastField>;
}

export default function EditGameMetadataModal({gameId, newGame, isOpen, onOpenChange}: EditGameMetadataModalProps) {
    const availablePlatforms = useSnapshot(platformState).available;
    const [propertyEnumValues, setPropertyEnumValues] = useState<GameEnumPropertyValuesDto>();
    // The full game is only loaded once the modal has been opened
    const [requestedGameId, setRequestedGameId] = useState<number>();
    const {full: game} = useFullGame(requestedGameId === gameId ? gameId : undefined);
    const [initialValues, setInitialValues] = useState<GameMetadataFormValues>();
    const [saveError, setSaveError] = useState("");
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        if (!isOpen) {
            setInitialValues(undefined);
            setSaveError("");
        } else if (newGame && !initialValues) {
            setInitialValues(metadataInitialValues({
                id: 0, title: fileNameFromPath(newGame.path.replace(/[\\/]+$/, "")),
                libraryId: newGame.libraryId, platforms: [...newGame.platforms],
                metadata: {fileSize: 0, path: newGame.path} as GameMetadataFormValues["metadata"],
                createdAt: "", updatedAt: "", mediaOrder: []
            }));
        } else if (game && (!initialValues || initialValues.id !== gameId)) {
            setInitialValues(metadataInitialValues(game));
        }
    }, [isOpen, gameId, game, newGame, initialValues]);

    useEffect(() => {
        if (isOpen) setRequestedGameId(gameId);
    }, [isOpen, gameId]);

    useEffect(() => {
        GameEndpoint.getEnumPropertyValues().then(setPropertyEnumValues);
    }, []);

    return propertyEnumValues && initialValues && (newGame || initialValues.id === gameId) && isOpen && (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} variant="opaque"
                            isDismissable={!isSaving} isKeyboardDismissDisabled={isSaving}>
                <Modal.Container size="lg" className="max-w-3xl">
                    <Modal.Dialog>
                        {({close}) => {

                            async function updateGame(values: GameMetadataFormValues) {
                                setSaveError("");
                                setIsSaving(true);
                                try {
                                    const changed = metadataUpdate(initialValues!, values);
                                    if (newGame) {
                                        changed.title = values.title.trim();
                                        changed.platforms = values.platforms;
                                    }
                                    if (newGame || Object.keys(changed).length > 1) await saveMetadata(changed, values, newGame);
                                    close();
                                } catch (error) {
                                    setSaveError(error instanceof Error ? error.message : "Unable to save metadata. Try again.");
                                } finally {
                                    setIsSaving(false);
                                }
                            }

                            return (
                                <>
                                    <Modal.CloseTrigger isDisabled={isSaving}/>
                                    <Formik initialValues={initialValues}
                                            onSubmit={updateGame}
                                            validationSchema={Yup.object({
                                                title: Yup.string().trim().required("Title is required")
                                            })}
                                    >
                                        {(formik) => (
                                            <Form className="flex flex-col flex-1 min-h-0">
                                                <Modal.Header className="flex flex-col gap-1">
                                                    <Modal.Heading>{newGame ? "Manual import" : "Update game metadata"}</Modal.Heading>
                                                </Modal.Header>
                                                <Modal.Body>
                                                    <GameMediaEditor searchOnOpen={!newGame}>
                                                        {({artwork, gallery}) => <>
                                                            <MetadataField name="metadata.path">
                                                                <Input key="metadata.path" name="metadata.path" label="Path"
                                                                       isDisabled className="mb-0"/>
                                                            </MetadataField>
                                                            {artwork}
                                                            <div className="flex flex-row gap-4 mt-4">
                                                                <MetadataField name="title">
                                                                    <Input key="title" name="title" label="Title"
                                                                           isRequired/>
                                                                </MetadataField>
                                                                <MetadataField name="release">
                                                                    <DatePickerInput key="release" name="release"
                                                                                     label="Release"
                                                                                     className="w-fit"/>
                                                                </MetadataField>
                                                            </div>
                                                            <MetadataField name="platforms" key={Array.from(availablePlatforms).join(",")}>
                                                                <AutocompleteInput options={Array.from(availablePlatforms)}
                                                                                   name="platforms" label="Platforms"/>
                                                            </MetadataField>
                                                            <MetadataField name="summary">
                                                                <TextAreaInput key="summary" name="summary"
                                                                               label="Summary (HTML)"/>
                                                            </MetadataField>
                                                            <MetadataField name="comment">
                                                                <TextAreaInput key="comment" name="comment"
                                                                               label="Comment (Markdown)"/>
                                                            </MetadataField>
                                                            {gallery}
                                                            <Accordion variant="default" className="space-y-2">
                                                                <Accordion.Item id="additional-metadata">
                                                                    <Accordion.Heading>
                                                                        <Accordion.Trigger
                                                                            className="bg-segment rounded-2xl mb-4">
                                                                            <span>Additional Metadata</span>
                                                                            <Accordion.Indicator/>
                                                                        </Accordion.Trigger>
                                                                    </Accordion.Heading>
                                                                    <Accordion.Panel>
                                                                        <Accordion.Body className="overflow-y-auto">
                                                                            <MetadataField name="developers">
                                                                                <ArrayInput key="developers"
                                                                                            name="developers"
                                                                                            label="Developers"/>
                                                                            </MetadataField>
                                                                            <MetadataField name="publishers">
                                                                                <ArrayInput key="publishers"
                                                                                            name="publishers"
                                                                                            label="Publishers"/>
                                                                            </MetadataField>
                                                                            <MetadataField name="genres">
                                                                                <AutocompleteInput
                                                                                    options={propertyEnumValues.genres}
                                                                                    defaultSelected={initialValues.genres}
                                                                                    key="genres" name="genres"
                                                                                    label="Genres"/>
                                                                            </MetadataField>
                                                                            <MetadataField name="themes">
                                                                                <AutocompleteInput
                                                                                    options={propertyEnumValues.themes}
                                                                                    defaultSelected={initialValues.themes}
                                                                                    key="themes" name="themes"
                                                                                    label="Themes"/>
                                                                            </MetadataField>
                                                                            <MetadataField name="features">
                                                                                <AutocompleteInput
                                                                                    options={propertyEnumValues.features}
                                                                                    defaultSelected={initialValues.features}
                                                                                    key="features" name="features"
                                                                                    label="Features"/>
                                                                            </MetadataField>
                                                                            <MetadataField name="perspectives">
                                                                                <AutocompleteInput
                                                                                    options={propertyEnumValues.perspectives}
                                                                                    defaultSelected={initialValues.perspectives}
                                                                                    key="perspectives" name="perspectives"
                                                                                    label="Perspectives"/>
                                                                            </MetadataField>
                                                                            <MetadataField name="keywords">
                                                                                <ArrayInput key="keywords" name="keywords"
                                                                                            label="Keywords"/>
                                                                            </MetadataField>
                                                                        </Accordion.Body>
                                                                    </Accordion.Panel>
                                                                </Accordion.Item>
                                                            </Accordion>
                                                        </>}
                                                    </GameMediaEditor>
                                                </Modal.Body>
                                                {saveError &&
                                                    <p role="alert" className="px-6 text-danger">{saveError}</p>}
                                                <Modal.Footer>
                                                    <Button variant="ghost" onPress={close} isDisabled={formik.isSubmitting}>
                                                        Cancel
                                                    </Button>
                                                    <Button
                                                        variant="primary"
                                                        isPending={formik.isSubmitting}
                                                        isDisabled={formik.isSubmitting || (!newGame && !formik.dirty)}
                                                        type="submit"
                                                    >
                                                        {formik.isSubmitting ?
                                                            <Spinner size="sm" color="current"/> : "Save"}
                                                    </Button>
                                                </Modal.Footer>
                                            </Form>
                                        )}
                                    </Formik>
                                </>
                            )
                        }}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}
