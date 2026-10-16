import React, {useEffect, useRef, useState} from "react";
import {Alert, Button, Checkbox, Modal, Spinner, Table, toast, Tooltip, useOverlayState} from "@heroui/react";
import {
    ArrowCounterClockwiseIcon,
    DatabaseIcon,
    DownloadSimpleIcon,
    TrashIcon,
    UploadSimpleIcon
} from "@phosphor-icons/react";
import {BackupEndpoint} from "Frontend/generated/endpoints";
import BackupDto from "Frontend/generated/org/gameyfin/app/core/backup/BackupDto";
import RestoreStatusDto from "Frontend/generated/org/gameyfin/app/core/backup/RestoreStatusDto";
import Section from "Frontend/components/general/Section";
import {humanFileSize} from "Frontend/util/utils";
import {uploadFile, waitForRestart} from "Frontend/util/restart";
import {useSnapshot} from "valtio/react";
import {backupState, trackBackup} from "Frontend/state/BackupState";

export default function BackupManagement() {
    const [backups, setBackups] = useState<BackupDto[]>([]);
    const [lastRestore, setLastRestore] = useState<RestoreStatusDto>();
    const [includeMedia, setIncludeMedia] = useState(false);
    const [starting, setStarting] = useState(false);
    const backupRunning = useSnapshot(backupState).isRunning;
    const completedBackups = useSnapshot(backupState).completedCount;
    const creating = starting || backupRunning;
    const [uploading, setUploading] = useState(false);
    const [restoring, setRestoring] = useState(false);
    const [selectedBackup, setSelectedBackup] = useState<BackupDto>();
    const restoreModal = useOverlayState();
    const deleteModal = useOverlayState();
    const fileInput = useRef<HTMLInputElement>(null);

    useEffect(() => {
        BackupEndpoint.getLastRestoreStatus().then(setLastRestore);
    }, []);

    // Reload whenever a backup has been completed in the background
    useEffect(() => {
        loadBackups();
    }, [completedBackups]);

    async function loadBackups() {
        setBackups(await BackupEndpoint.getBackups());
    }

    async function createBackup() {
        setStarting(true);
        try {
            trackBackup(await BackupEndpoint.createBackup(includeMedia));
            toast.info("Backup started", {description: "The progress is shown in the task overview at the top"});
        } catch (e: any) {
            toast.danger("Could not create backup", {description: e?.message});
        } finally {
            setStarting(false);
        }
    }

    async function uploadBackup(file: File) {
        setUploading(true);
        try {
            await uploadFile("/backups/upload", file);
            toast.success("Backup uploaded", {description: file.name});
            await loadBackups();
        } catch (e: any) {
            toast.danger("Could not upload backup", {description: e?.message});
        } finally {
            setUploading(false);
            if (fileInput.current) fileInput.current.value = "";
        }
    }

    function downloadBackup(backup: BackupDto) {
        const link = document.createElement("a");
        link.href = `/backups/${encodeURIComponent(backup.name)}`;
        link.download = backup.name;
        document.body.appendChild(link);
        link.click();
        link.remove();
    }

    async function deleteBackup(backup: BackupDto) {
        try {
            await BackupEndpoint.deleteBackup(backup.name);
            await loadBackups();
        } catch (e: any) {
            toast.danger("Could not delete backup", {description: e?.message});
        }
    }

    async function restoreBackup(backup: BackupDto) {
        setRestoring(true);
        try {
            await BackupEndpoint.restoreBackup(backup.name);
        } catch (e: any) {
            setRestoring(false);
            toast.danger("Could not restore backup", {description: e?.message});
            return;
        }

        await waitForRestart();
        // All sessions are invalidated by the restart, so this will redirect to the login page
        window.location.reload();
    }

    return (
        <div className="flex flex-col">
            <Section title="Backup & restore"/>
            <p className="text-sm text-muted mb-4">
                Backups contain all data stored in the database and can be restored on any supported database
                (e.g. a backup of the embedded H2 database can be restored on PostgreSQL).
                Images can optionally be included. Restoring a backup requires Gameyfin to use the same
                {" "}<code>APP_KEY</code> as when the backup was created.
            </p>

            {lastRestore &&
                <Alert status={lastRestore.success ? "success" : "danger"} className="mb-4">
                    <Alert.Indicator/>
                    <Alert.Content>
                        <Alert.Title className="font-bold">
                            {lastRestore.success ? "Last restore succeeded" : "Last restore failed"}
                        </Alert.Title>
                        <Alert.Description>
                            {`${lastRestore.backupName} (${new Date(lastRestore.finishedAt).toLocaleString()}): ${lastRestore.message}`}
                        </Alert.Description>
                    </Alert.Content>
                </Alert>
            }

            <div className="flex flex-row flex-wrap items-center gap-4 mb-4">
                <Button onPress={createBackup} isPending={creating} isDisabled={uploading || restoring}>
                    {creating ? <Spinner size="sm" color="current"/> : <DatabaseIcon/>}
                    Create backup
                </Button>
                <Checkbox isSelected={includeMedia} onChange={setIncludeMedia} isDisabled={creating}>
                    <Checkbox.Content>
                        <Checkbox.Control>
                            <Checkbox.Indicator/>
                        </Checkbox.Control>
                        Include images (may result in a large backup)
                    </Checkbox.Content>
                </Checkbox>
                <div className="grow"/>
                <Button variant="ghost"
                        onPress={() => fileInput.current?.click()}
                        isPending={uploading}
                        isDisabled={creating || restoring}>
                    {uploading ? <Spinner size="sm" color="current"/> : <UploadSimpleIcon/>}
                    Upload backup
                </Button>
                <input ref={fileInput}
                       type="file"
                       accept=".zip,application/zip"
                       className="hidden"
                       onChange={(e) => {
                           const file = e.target.files?.[0];
                           if (file) uploadBackup(file);
                       }}
                />
            </div>

            <Table variant="secondary">
                <Table.ScrollContainer>
                    <Table.Content aria-label="Backups">
                        <Table.Header>
                            <Table.Column id="name" isRowHeader>Name</Table.Column>
                            <Table.Column id="created">Created</Table.Column>
                            <Table.Column id="size">Size</Table.Column>
                            <Table.Column id="source">Source</Table.Column>
                            <Table.Column id="media">Images</Table.Column>
                            <Table.Column id="actions">Actions</Table.Column>
                        </Table.Header>
                        <Table.Body items={backups.map(b => ({key: b.name, backup: b}))}
                                    renderEmptyState={() =>
                                        <p className="text-center text-muted p-4">No backups available.</p>}>
                            {({backup}) => (
                                <Table.Row key={backup.name}>
                                    <Table.Cell>{backup.name}</Table.Cell>
                                    <Table.Cell>{new Date(backup.createdAt).toLocaleString()}</Table.Cell>
                                    <Table.Cell>{humanFileSize(backup.sizeBytes)}</Table.Cell>
                                    <Table.Cell>
                                        {[backup.databaseProduct, backup.gameyfinVersion && `Gameyfin ${backup.gameyfinVersion}`]
                                            .filter(Boolean).join(", ") || "Unknown"}
                                    </Table.Cell>
                                    <Table.Cell>{backup.includesMedia ? "Included" : "Not included"}</Table.Cell>
                                    <Table.Cell>
                                        <div className="flex flex-row gap-2">
                                            <Tooltip>
                                                <Tooltip.Trigger>
                                                    <Button isIconOnly size="sm" variant="ghost"
                                                            onPress={() => downloadBackup(backup)}>
                                                        <DownloadSimpleIcon/>
                                                    </Button>
                                                </Tooltip.Trigger>
                                                <Tooltip.Content>Download</Tooltip.Content>
                                            </Tooltip>
                                            <Tooltip>
                                                <Tooltip.Trigger>
                                                    {/* Wrapper keeps the tooltip working for disabled buttons */}
                                                    <span>
                                                        <Button isIconOnly size="sm" variant="ghost"
                                                                isDisabled={!backup.restorable || restoring || creating || uploading}
                                                                onPress={() => {
                                                                    setSelectedBackup(backup);
                                                                    restoreModal.open();
                                                                }}>
                                                            <ArrowCounterClockwiseIcon/>
                                                        </Button>
                                                    </span>
                                                </Tooltip.Trigger>
                                                <Tooltip.Content>
                                                    {backup.restorable ? "Restore" : `Can not be restored: ${backup.problem}`}
                                                </Tooltip.Content>
                                            </Tooltip>
                                            <Tooltip>
                                                <Tooltip.Trigger>
                                                    <Button isIconOnly size="sm" variant="danger"
                                                            isDisabled={restoring}
                                                            onPress={() => {
                                                                setSelectedBackup(backup);
                                                                deleteModal.open();
                                                            }}>
                                                        <TrashIcon/>
                                                    </Button>
                                                </Tooltip.Trigger>
                                                <Tooltip.Content>Delete</Tooltip.Content>
                                            </Tooltip>
                                        </div>
                                    </Table.Cell>
                                </Table.Row>
                            )}
                        </Table.Body>
                    </Table.Content>
                </Table.ScrollContainer>
            </Table>

            {selectedBackup &&
                <ConfirmModal isOpen={restoreModal.isOpen}
                              onOpenChange={restoreModal.setOpen}
                              title="Restore backup"
                              confirmLabel="Restore"
                              isPending={restoring}
                              onConfirm={() => restoreBackup(selectedBackup)}>
                    <p>
                        All data currently stored by Gameyfin will be replaced by the content of
                        {" "}<code>{selectedBackup.name}</code>.
                    </p>
                    <p>
                        A backup of the current database is created first. Gameyfin will then restart and
                        all users will be logged out.
                    </p>
                    {restoring &&
                        <p className="flex flex-row items-center gap-2 font-semibold">
                            <Spinner size="sm" color="current"/>
                            Restoring backup, please wait...
                        </p>
                    }
                </ConfirmModal>
            }

            {selectedBackup &&
                <ConfirmModal isOpen={deleteModal.isOpen}
                              onOpenChange={deleteModal.setOpen}
                              title="Delete backup"
                              confirmLabel="Delete"
                              onConfirm={async () => {
                                  await deleteBackup(selectedBackup);
                                  deleteModal.close();
                              }}>
                    <p>Delete backup <code>{selectedBackup.name}</code>? This can not be undone.</p>
                </ConfirmModal>
            }
        </div>
    );
}

interface ConfirmModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    title: string;
    confirmLabel: string;
    isPending?: boolean;
    onConfirm: () => void;
    children: React.ReactNode;
}

function ConfirmModal({isOpen, onOpenChange, title, confirmLabel, isPending, onConfirm, children}: ConfirmModalProps) {
    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen}
                            onOpenChange={(open) => !isPending && onOpenChange(open)}
                            variant="opaque"
                            isDismissable={!isPending}>
                <Modal.Container size="lg">
                    <Modal.Dialog>
                        {({close}) => (
                            <>
                                <Modal.Header>
                                    <Modal.Heading>{title}</Modal.Heading>
                                </Modal.Header>
                                <Modal.Body className="flex flex-col gap-2">
                                    {children}
                                </Modal.Body>
                                <Modal.Footer>
                                    <Button variant="ghost" onPress={close} isDisabled={isPending}>
                                        Cancel
                                    </Button>
                                    <Button variant="danger" onPress={onConfirm} isPending={isPending}>
                                        {isPending && <Spinner size="sm" color="current"/>}
                                        {confirmLabel}
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
