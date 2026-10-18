import React, {useRef, useState} from "react";
import {Alert, Button, Modal, Spinner} from "@heroui/react";
import {UploadSimpleIcon} from "@phosphor-icons/react";
import {SetupEndpoint} from "Frontend/generated/endpoints";
import {uploadFile, waitForRestart} from "Frontend/util/restart";

interface RestoreBackupModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    onRestored: () => void;
}

/**
 * Restores a backup during the initial setup (no user account exists at this point).
 */
export default function RestoreBackupModal({isOpen, onOpenChange, onRestored}: RestoreBackupModalProps) {
    const [file, setFile] = useState<File>();
    const fileInput = useRef<HTMLInputElement>(null);
    const [state, setState] = useState<"idle" | "uploading" | "restoring">("idle");
    const [error, setError] = useState<string>();

    const busy = state !== "idle";

    async function restore() {
        if (!file) return;
        setError(undefined);
        setState("uploading");

        try {
            await uploadFile("/backups/setup/restore", file);
        } catch (e: any) {
            setError(e?.message ?? "Upload failed");
            setState("idle");
            return;
        }

        setState("restoring");
        const restarted = await waitForRestart();
        if (!restarted) {
            setError("Gameyfin did not restart in time. Please check the logs.");
            setState("idle");
            return;
        }

        try {
            if (await SetupEndpoint.isSetupCompleted()) {
                onRestored();
                return;
            }
            const status = await SetupEndpoint.getLastRestoreStatus();
            if (status && !status.success) {
                setError(`Restore failed: ${status.message}`);
            } else {
                setError("The backup has been restored, but it does not contain an administrator account.");
            }
        } catch (e: any) {
            setError(e?.message ?? "Could not determine the result of the restore");
        }
        setState("idle");
    }

    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen}
                            onOpenChange={(open) => !busy && onOpenChange(open)}
                            variant="opaque"
                            isDismissable={!busy}>
                <Modal.Container size="lg">
                    <Modal.Dialog>
                        {({close}) => (
                            <>
                                <Modal.Header>
                                    <Modal.Heading>Restore from backup</Modal.Heading>
                                </Modal.Header>
                                <Modal.Body className="flex flex-col gap-4">
                                    <p>
                                        Restore all data from a backup created by Gameyfin (e.g. to migrate to a
                                        different database). The backup can be created in the
                                        "System" section of the administration area of your existing instance.
                                    </p>
                                    <p>
                                        Make sure <code>APP_KEY</code> is set to the same value as on the instance
                                        the backup was created on. Gameyfin restarts to restore the backup, after which
                                        you can log in with your existing account.
                                    </p>
                                    <div className="flex flex-row items-center gap-4">
                                        <Button variant="ghost"
                                                onPress={() => fileInput.current?.click()}
                                                isDisabled={busy}>
                                            <UploadSimpleIcon/>
                                            Upload backup
                                        </Button>
                                        {file && <span className="truncate">{file.name}</span>}
                                    </div>
                                    <input ref={fileInput}
                                           type="file"
                                           accept=".zip,application/zip"
                                           className="hidden"
                                           onChange={(e) => setFile(e.target.files?.[0])}
                                    />
                                    {error &&
                                        <Alert status="danger">
                                            <Alert.Indicator/>
                                            <Alert.Content>
                                                <Alert.Description>{error}</Alert.Description>
                                            </Alert.Content>
                                        </Alert>
                                    }
                                    {busy &&
                                        <p className="flex flex-row items-center gap-2 font-semibold">
                                            <Spinner size="sm" color="current"/>
                                            {state === "uploading" ? "Uploading backup..." : "Restoring backup, please wait..."}
                                        </p>
                                    }
                                </Modal.Body>
                                <Modal.Footer>
                                    <Button variant="ghost" onPress={close} isDisabled={busy}>
                                        Cancel
                                    </Button>
                                    <Button variant="primary" onPress={restore} isDisabled={!file} isPending={busy}>
                                        {busy && <Spinner size="sm" color="current"/>}
                                        Restore
                                    </Button>
                                </Modal.Footer>
                            </>
                        )}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}
