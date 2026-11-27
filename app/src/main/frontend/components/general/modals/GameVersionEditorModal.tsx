import {useState} from "react";
import {Button, Modal, Spinner} from "@heroui/react";
import {StackIcon} from "@phosphor-icons/react";
import ResponsiveDialog from "Frontend/components/layout/ResponsiveDialog";
import GameVersionEditor from "Frontend/components/general/versions/GameVersionEditor";
import {useGameVersionEditor} from "Frontend/components/general/versions/useGameVersionEditor";

interface GameVersionEditorModalProps {
    gameId: number;
    gameTitle: string;
    onClose: () => void;
}

export default function GameVersionEditorModal({gameId, gameTitle, onClose}: GameVersionEditorModalProps) {
    const editor = useGameVersionEditor(gameId);
    const [saving, setSaving] = useState(false);
    const [saveError, setSaveError] = useState(false);

    async function save() {
        setSaving(true);
        setSaveError(false);
        try {
            await editor.apply();
            onClose();
        } catch {
            setSaveError(true);
        } finally {
            setSaving(false);
        }
    }

    return (
        <Modal>
            <Modal.Backdrop isOpen isDismissable={false} isKeyboardDismissDisabled={saving}
                            onOpenChange={(open) => {
                                if (!open && !saving) onClose();
                            }} variant="opaque">
                <ResponsiveDialog size="lg" scroll="inside" className="max-w-3xl">
                    <Modal.Dialog>
                        <Modal.CloseTrigger isDisabled={saving}/>
                        <Modal.Header>
                            <Modal.Heading
                                className="flex items-center gap-2"><StackIcon/> Versions &amp; Extras</Modal.Heading>
                            <p className="text-sm text-muted">{gameTitle}</p>
                        </Modal.Header>
                        <Modal.Body inert={saving} aria-busy={saving || editor.loading}>
                            <GameVersionEditor gameId={gameId} gameTitle={gameTitle} editor={editor}/>
                            {saveError &&
                                <p role="alert" className="mt-3 text-danger">Some changes may have been saved. Review
                                    the store details before trying again.</p>}
                        </Modal.Body>
                        <Modal.Footer>
                            <Button variant="tertiary" isDisabled={saving} onPress={onClose}>Cancel</Button>
                            <Button isPending={saving} isDisabled={!editor.dirty || saving} onPress={save}>
                                {saving && <Spinner size="sm" color="current"/>}Save
                            </Button>
                        </Modal.Footer>
                    </Modal.Dialog>
                </ResponsiveDialog>
            </Modal.Backdrop>
        </Modal>
    );
}
