import {Accordion, Button, Modal, Spinner} from "@heroui/react";
import {Form, Formik} from "formik";
import Input from "Frontend/components/general/input/Input";
import React, {useEffect, useState} from "react";
import GameEnumPropertyValuesDto from "Frontend/generated/org/gameyfin/app/games/dto/GameEnumPropertyValuesDto";
import {GameEndpoint} from "Frontend/generated/endpoints";
import TextAreaInput from "Frontend/components/general/input/TextAreaInput";
import * as Yup from "yup";
import DatePickerInput from "Frontend/components/general/input/DatePickerInput";
import ArrayInput from "Frontend/components/general/input/ArrayInput";
import GameMediaEditor from "Frontend/components/general/input/GameMediaEditor";
import {GameMetadataFormValues, metadataInitialValues, metadataUpdate, saveMetadata} from "Frontend/util/gameMedia";
import AutocompleteInput from "Frontend/components/general/input/AutocompleteInput.tsx";
import {useSnapshot} from "valtio/react";
import {platformState} from "Frontend/state/PlatformState";
import {useFullGame} from "Frontend/state/GameState";

interface EditGameMetadataModalProps {
    gameId: number;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
}

export default function EditGameMetadataModal({gameId, isOpen, onOpenChange}: EditGameMetadataModalProps) {
    const availablePlatforms = useSnapshot(platformState).available;
    const [propertyEnumValues, setPropertyEnumValues] = useState<GameEnumPropertyValuesDto>();
    // The full game is only loaded once the modal has been opened
    const [requestedGameId, setRequestedGameId] = useState<number>();
    const {full: game} = useFullGame(requestedGameId === gameId ? gameId : undefined);
    const [initialValues, setInitialValues] = useState<GameMetadataFormValues>();
    const [saveError, setSaveError] = useState("");

    useEffect(() => {
        if (!isOpen) {
            setInitialValues(undefined);
            setSaveError("");
        } else if (game && (!initialValues || initialValues.id !== gameId)) {
            setInitialValues(metadataInitialValues(game));
        }
    }, [isOpen, gameId, game, initialValues]);

    useEffect(() => {
        if (isOpen) setRequestedGameId(gameId);
    }, [isOpen, gameId]);

    useEffect(() => {
        GameEndpoint.getEnumPropertyValues().then(setPropertyEnumValues);
    }, []);

    return propertyEnumValues && initialValues && initialValues.id === gameId && isOpen && (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} variant="opaque">
                <Modal.Container size="lg" className="max-w-3xl">
                    <Modal.Dialog>
                        {({close}) => {

                            async function updateGame(values: GameMetadataFormValues) {
                                setSaveError("");
                                try {
                                    const changed = metadataUpdate(initialValues!, values);
                                    if (Object.keys(changed).length > 1) await saveMetadata(changed, values);
                                    close();
                                } catch (error) {
                                    setSaveError(error instanceof Error ? error.message : "Unable to save metadata. Try again.");
                                }
                            }

                            return (
                                <>
                                    <Modal.CloseTrigger/>
                                    <Formik initialValues={initialValues}
                                            onSubmit={updateGame}
                                            validationSchema={Yup.object({
                                                title: Yup.string().required("Title is required")
                                            })}
                                    >
                                        {(formik) => (
                                            <Form className="flex flex-col flex-1 min-h-0">
                                                <Modal.Header className="flex flex-col gap-1">
                                                    <Modal.Heading>Update game metadata</Modal.Heading>
                                                </Modal.Header>
                                                <Modal.Body>
                                                    <GameMediaEditor>
                                                        {({artwork, gallery}) => <>
                                                            <Input key="metadata.path" name="metadata.path" label="Path"
                                                                   isDisabled className="mb-0"/>
                                                            {artwork}
                                                            <div className="flex flex-row gap-4 mt-4">
                                                                <Input key="title" name="title" label="Title"
                                                                       isRequired/>
                                                                <DatePickerInput key="release" name="release"
                                                                                 label="Release"
                                                                                 className="w-fit"/>
                                                            </div>
                                                            <AutocompleteInput options={Array.from(availablePlatforms)}
                                                                               name="platforms" label="Platforms"/>
                                                            <TextAreaInput key="summary" name="summary"
                                                                           label="Summary (HTML)"/>
                                                            <TextAreaInput key="comment" name="comment"
                                                                           label="Comment (Markdown)"/>
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
                                                                            <ArrayInput key="developers"
                                                                                        name="developers"
                                                                                        label="Developers"/>
                                                                            <ArrayInput key="publishers"
                                                                                        name="publishers"
                                                                                        label="Publishers"/>
                                                                            <AutocompleteInput
                                                                                options={propertyEnumValues.genres}
                                                                                defaultSelected={initialValues.genres}
                                                                                key="genres" name="genres"
                                                                                label="Genres"/>
                                                                            <AutocompleteInput
                                                                                options={propertyEnumValues.themes}
                                                                                defaultSelected={initialValues.themes}
                                                                                key="themes" name="themes"
                                                                                label="Themes"/>
                                                                            <AutocompleteInput
                                                                                options={propertyEnumValues.features}
                                                                                defaultSelected={initialValues.features}
                                                                                key="features" name="features"
                                                                                label="Features"/>
                                                                            <AutocompleteInput
                                                                                options={propertyEnumValues.perspectives}
                                                                                defaultSelected={initialValues.perspectives}
                                                                                key="perspectives" name="perspectives"
                                                                                label="Perspectives"/>
                                                                            <ArrayInput key="keywords" name="keywords"
                                                                                        label="Keywords"/>
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
                                                    <Button variant="ghost" onPress={close}>
                                                        Cancel
                                                    </Button>
                                                    <Button
                                                        variant="primary"
                                                        isPending={formik.isSubmitting}
                                                        isDisabled={formik.isSubmitting || !formik.dirty}
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
