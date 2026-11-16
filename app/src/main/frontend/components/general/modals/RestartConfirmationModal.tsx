import {useRef, useState} from "react";
import {toast} from "@heroui/react";
import {SystemEndpoint} from "Frontend/generated/endpoints";
import ConfirmModal from "Frontend/components/general/modals/ConfirmModal";
import {waitForRestart} from "Frontend/util/restart";

interface RestartConfirmationModalProps {
    isOpen: boolean;
    onOpenChange: (open: boolean) => void;
    onConfirm?: () => Promise<void>;
}

/** Shared confirmation for restart actions in settings and notifications. */
export default function RestartConfirmationModal({
                                                     isOpen, onOpenChange, onConfirm = () => SystemEndpoint.restart()
                                                 }: RestartConfirmationModalProps) {
    const pending = useRef(false);
    const [isPending, setPending] = useState(false);
    const [error, setError] = useState<string>();

    async function restart() {
        if (pending.current) return;
        pending.current = true;
        setPending(true);
        setError(undefined);

        try {
            await onConfirm();
        } catch (error) {
            setError(error instanceof Error ? error.message : "Could not restart Gameyfin. Please try again.");
            pending.current = false;
            setPending(false);
            return;
        }

        onOpenChange(false);
        toast.success("Restarting", {
            description: "Gameyfin is restarting. This page will reload when it is ready."
        });
        try {
            if (await waitForRestart()) {
                window.location.reload();
            } else {
                toast.danger("Restart timed out", {
                    description: "Gameyfin did not become available in time. Please check the server logs."
                });
            }
        } finally {
            pending.current = false;
            setPending(false);
        }
    }

    return <ConfirmModal
        isOpen={isOpen}
        onOpenChange={open => {
            if (!open) setError(undefined);
            onOpenChange(open);
        }}
        title="Restart Gameyfin?"
        confirmLabel="Restart"
        isPending={isPending}
        onConfirm={() => void restart()}
    >
        <p>Restarting interrupts active sessions and work, including downloads and running tasks.</p>
        <p>This page will reload when Gameyfin is available again. You may need to sign in again.</p>
        {error && <p role="alert" className="text-danger">{error}</p>}
    </ConfirmModal>;
}
