import GameMetadataFields from "Frontend/components/general/input/GameMetadataFields";
import ResponsiveDialog from "Frontend/components/layout/ResponsiveDialog";
import {Accordion, Button, Modal, Spinner} from "@heroui/react";
import {Form, Formik} from "formik";
import React, {useEffect, useState} from "react";
import GameEnumPropertyValuesDto from "Frontend/generated/org/gameyfin/app/games/dto/GameEnumPropertyValuesDto";
import {GameEndpoint} from "Frontend/generated/endpoints";
import * as Yup from "yup";
import GameVersionEditor from "Frontend/components/general/versions/GameVersionEditor";
import {useGameVersionEditor} from "Frontend/components/general/versions/useGameVersionEditor";
import {
    GameMetadataFormValues,
    ManualGameImport,
    metadataInitialValues,
    metadataUpdate,
    saveMetadata
} from "Frontend/util/gameMedia";
import {useFullGame} from "Frontend/state/GameState";
import {fileNameFromPath} from "Frontend/util/utils";

type EditGameMetadataModalProps =
    ({ gameId: number; newGame?: never } | { gameId?: never; newGame: ManualGameImport })
    & {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;

}

export default function EditGameMetadataModal({gameId, newGame, isOpen, onOpenChange}: EditGameMetadataModalProps) {
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
    const versionEditor = useGameVersionEditor(gameId ?? 0, isOpen && gameId !== undefined);

    useEffect(() => {
        GameEndpoint.getEnumPropertyValues().then(setPropertyEnumValues);
    }, []);

    return propertyEnumValues && initialValues && (newGame || initialValues.id === gameId) && isOpen && (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} isDismissable={false} variant="opaque">
                <ResponsiveDialog size="lg" className="max-w-3xl">
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
                                    if (!newGame) {
                                        try {
                                            await versionEditor.apply();
                                        } catch {
                                            setSaveError("Metadata was saved, but some store-version changes may not have been saved. Check the notification and review the store section.");
                                            return;
                                        }
                                    }
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
                                                <Modal.Body inert={isSaving} aria-busy={isSaving}>
                                                    <GameMetadataFields initialValues={initialValues}
                                                                        propertyEnumValues={propertyEnumValues}
                                                                        searchOnOpen={!newGame}/>
                                                    {versionEditor.available && !newGame && gameId !== undefined && game && (
                                                        <Accordion variant="default" className="space-y-2">
                                                            <Accordion.Item id="store-versions">
                                                                <Accordion.Heading>
                                                                    <Accordion.Trigger className="bg-segment rounded-2xl mb-4">
                                                                        <span>Store versions</span>
                                                                        <Accordion.Indicator/>
                                                                    </Accordion.Trigger>
                                                                </Accordion.Heading>
                                                                <Accordion.Panel>
                                                                    <Accordion.Body className="max-h-80 overflow-y-auto">
                                                                        <GameVersionEditor gameId={gameId}
                                                                                           gameTitle={game.title}
                                                                                           editor={versionEditor}/>
                                                                    </Accordion.Body>
                                                                </Accordion.Panel>
                                                            </Accordion.Item>
                                                        </Accordion>
                                                    )}
                                                </Modal.Body>
                                                {saveError &&
                                                    <p role="alert" className="px-6 text-danger">{saveError}</p>}
                                                <Modal.Footer>
                                                    <Button variant="ghost" onPress={close}
                                                            isDisabled={formik.isSubmitting}>
                                                        Cancel
                                                    </Button>
                                                    <Button
                                                        variant="primary"
                                                        isPending={formik.isSubmitting}
                                                        isDisabled={formik.isSubmitting || (!newGame && !formik.dirty && !versionEditor.dirty)}
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
                </ResponsiveDialog>
            </Modal.Backdrop>
        </Modal>
    );
}
