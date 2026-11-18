import {useEffect, useState} from "react";
import {Checkbox, Description, toast} from "@heroui/react";
import {WarningIcon} from "@phosphor-icons/react";
import {GameEndpoint} from "Frontend/generated/endpoints";
import ConfirmModal from "Frontend/components/general/modals/ConfirmModal";

interface RemoveGameModalProps {
    game: {id: number; title: string; path?: string | null};
    isOpen: boolean;
    onOpenChange: (open: boolean) => void;
    onRemoved?: () => void;
}

export default function RemoveGameModal({game, isOpen, onOpenChange, onRemoved}: RemoveGameModalProps) {
    const [deleteFiles, setDeleteFiles] = useState(false);
    const [isPending, setIsPending] = useState(false);

    useEffect(() => {
        setDeleteFiles(false);
    }, [isOpen, game.id]);

    async function removeGame() {
        setIsPending(true);
        try {
            await GameEndpoint.deleteGame(game.id, deleteFiles);
            toast.success("Game removed", {description: `${game.title} removed from the library.`});
            onOpenChange(false);
            onRemoved?.();
        } catch {
            toast.danger("Could not remove game", {
                description: deleteFiles
                    ? "Check that the game files are accessible and try again. Files may have been partially deleted."
                    : "The game could not be removed. Please try again.",
            });
        } finally {
            setIsPending(false);
        }
    }

    return <ConfirmModal isOpen={isOpen} onOpenChange={onOpenChange}
                         title="Remove from library" confirmLabel={deleteFiles ? "Remove and delete files" : "Remove"}
                         isPending={isPending} onConfirm={removeGame}>
        <p>Remove <strong>{game.title}</strong> from the library?</p>
        <Checkbox isSelected={deleteFiles} onChange={setDeleteFiles} isDisabled={isPending}>
            <Checkbox.Content>
                <Checkbox.Control><Checkbox.Indicator/></Checkbox.Control>
                Delete game files from disk
            </Checkbox.Content>
            <Description>Leave unchecked to keep the files.</Description>
        </Checkbox>
        {deleteFiles && <div role="alert" className="flex gap-3 rounded-xl border border-danger/30 bg-danger/10 p-4 text-danger">
            <WarningIcon className="mt-0.5 size-5 shrink-0" aria-hidden="true"/>
            <div className="min-w-0 space-y-2 text-sm">
                <p className="font-semibold">Files will be permanently deleted. This cannot be undone.</p>
                <p>The game file or folder and all its contents will be removed from disk.</p>
                {game.path && <p className="break-all font-mono text-xs">{game.path}</p>}
            </div>
        </div>}
    </ConfirmModal>;
}
