import {Button, Link, Popover, ProgressBar, ScrollShadow, Separator, Spinner} from "@heroui/react";
import {useSnapshot} from "valtio/react";
import {scanState} from "Frontend/state/ScanState";
import {libraryState} from "Frontend/state/LibraryState";
import {backupState} from "Frontend/state/BackupState";
import {ListChecksIcon, WarningIcon} from "@phosphor-icons/react";
import {humanFileSize, timeBetween, timeUntil, toTitleCase} from "Frontend/util/utils";
import LibraryScanStatus from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryScanStatus";
import type LibraryScanProgress from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryScanProgress";
import type LibraryDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryDto";
import BackupTaskStatus from "Frontend/generated/org/gameyfin/app/core/backup/BackupTaskStatus";
import BackupTrigger from "Frontend/generated/org/gameyfin/app/core/backup/BackupTrigger";
import type BackupProgress from "Frontend/generated/org/gameyfin/app/core/backup/BackupProgress";
import type ImageRecoveryProgress from "Frontend/generated/org/gameyfin/app/core/backup/ImageRecoveryProgress";
import {ReactNode, useEffect, useRef, useState} from "react";

type TaskStatus = "IN_PROGRESS" | "COMPLETED" | "FAILED";

/** Common representation of long-running background tasks (library scans, backups, ...) */
interface ActiveTask {
    id: string;
    title: ReactNode;
    status: TaskStatus;
    step: { description: string; current?: number; total?: number };
    startedAt: string;
    finishedAt?: string;
    /** Shown once the task has been completed */
    result?: ReactNode;
    /** Shown if the task failed */
    failure: ReactNode;
}

function scanToTask(scan: LibraryScanProgress, libraries: Record<number, LibraryDto>): ActiveTask {
    return {
        id: `scan-${scan.scanId}`,
        title: <>
            {toTitleCase(scan.type)} scan for library&nbsp;
            <Link className="underline text-sm" href={`/administration/games/library/${scan.libraryId}`}>
                {libraries[scan.libraryId]?.name}
            </Link>
        </>,
        status: toTaskStatus(scan.status),
        step: scan.currentStep,
        startedAt: scan.startedAt,
        finishedAt: scan.finishedAt,
        result: scan.finishedAt && <>
            {scan.result?.new} new /&nbsp;
            {(scan as any).result?.updated != null && `${(scan as any).result.updated} updated / `}
            {scan.result?.removed} removed /&nbsp;
            {scan.result?.unmatched} unmatched&nbsp;
            (in {timeBetween(scan.startedAt, scan.finishedAt)})
        </>,
        failure: "Scan failed (check logs for details)"
    };
}

function backupToTask(backup: BackupProgress): ActiveTask {
    return {
        id: `backup-${backup.backupId}`,
        title: <>
            {backup.trigger === BackupTrigger.PRE_RESTORE ? "Backup before restore" : "Backup"}
            {backup.includesMedia ? " (including images)" : ""}&nbsp;
            <Link className="underline text-sm" href="/administration/system">
                (show backups)
            </Link>
        </>,
        status: toTaskStatus(backup.status),
        step: backup.currentStep,
        startedAt: backup.startedAt,
        finishedAt: backup.finishedAt,
        result: backup.finishedAt && <>
            Created {backup.backupName}
            {backup.sizeBytes != null && ` (${humanFileSize(backup.sizeBytes)})`}&nbsp;
            in {timeBetween(backup.startedAt, backup.finishedAt)}
        </>,
        failure: `Backup failed: ${backup.errorMessage ?? "check logs for details"}`
    };
}

function imageRecoveryToTask(recovery: ImageRecoveryProgress): ActiveTask {
    return {
        id: `image-recovery-${recovery.taskId}`,
        title: "Recovering missing images after restore",
        status: toTaskStatus(recovery.status),
        step: recovery.currentStep,
        startedAt: recovery.startedAt,
        finishedAt: recovery.finishedAt,
        result: recovery.finishedAt && <>
            {recovery.restored} restored / {recovery.removed} removed / {recovery.failed} failed&nbsp;
            (in {timeBetween(recovery.startedAt, recovery.finishedAt)})
        </>,
        failure: `Restoring images failed: ${recovery.errorMessage ?? "check logs for details"}`
    };
}

function toTaskStatus(status: LibraryScanStatus | BackupTaskStatus): TaskStatus {
    switch (status) {
        case LibraryScanStatus.COMPLETED:
        case BackupTaskStatus.COMPLETED:
            return "COMPLETED";
        case LibraryScanStatus.FAILED:
        case BackupTaskStatus.FAILED:
            return "FAILED";
        default:
            return "IN_PROGRESS";
    }
}

export default function ActiveTaskPopover() {
    const libraries = useSnapshot(libraryState).state;
    const scans = useSnapshot(scanState).sortedByStartTime;
    const scanInProgress = useSnapshot(scanState).isScanning;
    const backups = useSnapshot(backupState).sortedByStartTime;
    const backupInProgress = useSnapshot(backupState).isRunning;
    const imageRecoveries = useSnapshot(backupState).imageRecovery;
    const imageRecoveryInProgress = useSnapshot(backupState).isImageRecoveryRunning;

    const tasks: ActiveTask[] = [
        ...scans.map(scan => scanToTask(scan as LibraryScanProgress, libraries as Record<number, LibraryDto>)),
        ...backups.map(backup => backupToTask(backup as BackupProgress)),
        ...Object.values(imageRecoveries).map(recovery => imageRecoveryToTask(recovery as ImageRecoveryProgress))
    ].sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime());

    // Add state to track current time and force re-renders
    const [_currentTime, setCurrentTime] = useState(Date.now());

    // Cache ETAs per task: { eta: string | null, computedAt: number }
    const etaCacheRef = useRef<Record<string, { eta: string | null; computedAt: number }>>({});

    // Set up an interval to update the time every second
    useEffect(() => {
        const intervalId = setInterval(() => {
            setCurrentTime(Date.now());
        }, 1000);

        // Clean up the interval when component unmounts
        return () => clearInterval(intervalId);
    }, []);

    function estimateTimeLeft(task: ActiveTask): string | null {
        const now = Date.now();
        const cached = etaCacheRef.current[task.id];
        // Only recompute every 5 seconds
        if (cached && now - cached.computedAt < 5000) {
            return cached.eta;
        }

        const current = task.step.current;
        const total = task.step.total;
        if (!current || !total || current <= 0 || total <= 0) {
            etaCacheRef.current[task.id] = {eta: null, computedAt: now};
            return null;
        }

        const elapsed = (now - new Date(task.startedAt).getTime()) / 1000;
        if (elapsed <= 0) {
            etaCacheRef.current[task.id] = {eta: null, computedAt: now};
            return null;
        }

        const rate = current / elapsed; // items per second
        const remaining = total - current;
        const secondsLeft = Math.round(remaining / rate);
        if (secondsLeft < 0) {
            etaCacheRef.current[task.id] = {eta: null, computedAt: now};
            return null;
        }

        const mins = Math.floor(secondsLeft / 60);
        const secs = secondsLeft % 60;
        const eta = `${mins}:${secs.toString().padStart(2, "0")} min left`;
        etaCacheRef.current[task.id] = {eta, computedAt: now};
        return eta;
    }

    return (
        <Popover>
            <Button isIconOnly variant="ghost" aria-label="Active tasks">
                {scanInProgress || backupInProgress || imageRecoveryInProgress ?
                    <Spinner size="sm" color="current"/> :
                    <ListChecksIcon className="fill-muted"/>
                }
            </Button>
            <Popover.Content placement="bottom end">
                <Popover.Dialog>
                    <Popover.Arrow/>
                    <div className="flex flex-col gap-2 m-2 min-w-md">
                        {tasks.length === 0 ?
                            <p className="flex h-12 items-center justify-center text-sm text-muted">
                                No tasks in progress or in history.
                            </p> :
                            <ScrollShadow hideScrollBar className="max-h-96">
                                {tasks.map((task, index) =>
                                    <div className="flex flex-col" key={task.id}>
                                        <div
                                            className="flex flex-row gap-4 justify-between items-center text-muted mb-1">
                                            <p>{task.title}</p>
                                            {task.finishedAt ?
                                                <p className="text-muted">
                                                    Finished {timeUntil(task.finishedAt)}
                                                </p> :
                                                <p className="text-muted">
                                                    Started {timeUntil(task.startedAt)}
                                                </p>
                                            }
                                        </div>
                                        {task.status === "IN_PROGRESS" &&
                                            (task.step.current && task.step.total ?
                                                    <div className="flex flex-col gap-1">
                                                        <div className="flex flex-row justify-between">
                                                            <p className="text-muted">
                                                                {`${task.step.description} (${task.step.current}/${task.step.total})`}
                                                            </p>
                                                            <p className="text-muted">
                                                                {estimateTimeLeft(task)}
                                                            </p>
                                                        </div>
                                                        <ProgressBar
                                                            value={task.step.current / task.step.total * 100}
                                                            size="sm">
                                                            <ProgressBar.Track>
                                                                <ProgressBar.Fill/>
                                                            </ProgressBar.Track>
                                                        </ProgressBar>
                                                    </div> :
                                                    <div className="flex flex-col gap-1">
                                                        <p className="text-muted">{task.step.description}</p>
                                                        <ProgressBar isIndeterminate size="sm">
                                                            <ProgressBar.Track>
                                                                <ProgressBar.Fill/>
                                                            </ProgressBar.Track>
                                                        </ProgressBar>
                                                    </div>
                                            )
                                        }
                                        {task.status === "COMPLETED" && <p>{task.result}</p>}
                                        {task.status === "FAILED" &&
                                            <p className="text-danger flex flex-row gap-1"><WarningIcon weight="fill"/>
                                                {task.failure}
                                            </p>
                                        }
                                        {tasks.length > 1 && index < (tasks.length - 1) &&
                                            <Separator className="my-2"/>}
                                    </div>
                                )}
                            </ScrollShadow>
                        }
                    </div>
                </Popover.Dialog>
            </Popover.Content>
        </Popover>
    );
}
