import React, {useRef, useState} from "react";
import {Button, toast, useOverlayState} from "@heroui/react";
import {TrashIcon} from "@phosphor-icons/react";
import ConfirmModal from "Frontend/components/general/modals/ConfirmModal";
import TaskRunDto from "Frontend/generated/org/gameyfin/app/core/tasks/dto/TaskRunDto";
import {trackRun} from "Frontend/state/TaskState";

interface BulkDeleteButtonProps {
    entityLabel: "libraries" | "collections";
    selectedItems: readonly { id: number; name: string }[];
    deleteItem: (id: number) => Promise<TaskRunDto>;
    onStarted: (ids: number[]) => void;
}

export default function BulkDeleteButton({entityLabel, selectedItems, deleteItem, onStarted}: BulkDeleteButtonProps) {
    const modal = useOverlayState();
    const [isDeleting, setIsDeleting] = useState(false);
    const deleting = useRef(false);

    async function handleDelete() {
        if (deleting.current || selectedItems.length === 0) return;
        deleting.current = true;
        setIsDeleting(true);
        const started: number[] = [];
        const failed: string[] = [];
        try {
            for (const item of selectedItems) {
                try {
                    const run = await deleteItem(item.id);
                    trackRun(run);
                    started.push(item.id);
                } catch {
                    failed.push(item.name);
                }
            }
            onStarted(started);
            if (failed.length > 0) {
                toast.warning(`Error deleting ${entityLabel}`, {
                    description: `Could not start deletion for: ${failed.join(", ")}.`
                });
            }
            modal.close();
        } finally {
            deleting.current = false;
            setIsDeleting(false);
        }
    }

    return <>
        <Button type="button"
                variant="danger-soft"
                isIconOnly
                isDisabled={selectedItems.length === 0 || isDeleting}
                onPress={modal.open}>
            <TrashIcon/>
        </Button>
        <ConfirmModal isOpen={modal.isOpen} onOpenChange={modal.setOpen}
                      title={`Delete selected ${entityLabel}`} confirmLabel="Delete"
                      isPending={isDeleting} onConfirm={handleDelete}>
            <p>Delete {selectedItems.length} selected {entityLabel}? This cannot be undone.</p>
            <ul className="list-disc pl-5">
                {selectedItems.map(item => <li key={item.id}>{item.name}</li>)}
            </ul>
        </ConfirmModal>
    </>;
}
