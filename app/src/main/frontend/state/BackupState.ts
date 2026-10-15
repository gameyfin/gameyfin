import {proxy} from 'valtio';
import {toast} from "@heroui/react";
import {Subscription} from "@vaadin/hilla-frontend";
import {BackupEndpoint} from "Frontend/generated/endpoints";
import type BackupProgress from "Frontend/generated/org/gameyfin/app/core/backup/BackupProgress";
import BackupTaskStatus from "Frontend/generated/org/gameyfin/app/core/backup/BackupTaskStatus";
import BackupTrigger from "Frontend/generated/org/gameyfin/app/core/backup/BackupTrigger";
import type ImageRecoveryProgress from "Frontend/generated/org/gameyfin/app/core/backup/ImageRecoveryProgress";

type BackupState = {
    subscription?: Subscription<BackupProgress[]>;
    state: Record<string, BackupProgress>;
    /** Incremented whenever a backup has been completed, can be used to reload the list of backups */
    completedCount: number;
    isRunning: boolean;
    sortedByStartTime: BackupProgress[];
    imageRecoverySubscription?: Subscription<ImageRecoveryProgress[]>;
    /** Recovery of images which are missing after a restore */
    imageRecovery: Record<string, ImageRecoveryProgress>;
    isImageRecoveryRunning: boolean;
};

export const backupState = proxy<BackupState>({
    state: {},
    completedCount: 0,
    imageRecovery: {},
    get isImageRecoveryRunning(): boolean {
        return Object.values(this.imageRecovery)
            .some((progress: ImageRecoveryProgress) => progress.status === BackupTaskStatus.IN_PROGRESS);
    },
    get isRunning(): boolean {
        return Object.values(this.state)
            .some((progress: BackupProgress) => progress.status === BackupTaskStatus.IN_PROGRESS);
    },
    get sortedByStartTime(): BackupProgress[] {
        return Object.values(this.state).sort((a: BackupProgress, b: BackupProgress) => {
            return new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime();
        });
    }
});

/** Subscribe to and process state updates from backend **/
export function initializeBackupState() {
    if (backupState.subscription) return;

    backupState.subscription = BackupEndpoint.subscribeToBackupProgressEvents().onNext((progresses: BackupProgress[]) => {
        progresses.forEach((progress: BackupProgress) => {
            const previous = backupState.state[progress.backupId];
            // A finished backup never becomes active again, so this can only be an outdated update
            if (previous && previous.status !== BackupTaskStatus.IN_PROGRESS && progress.status === BackupTaskStatus.IN_PROGRESS) return;
            backupState.state[progress.backupId] = progress;

            // Only notify about backups which have been observed while running
            if (previous?.status !== BackupTaskStatus.IN_PROGRESS || progress.status === BackupTaskStatus.IN_PROGRESS) return;
            if (progress.status === BackupTaskStatus.COMPLETED) backupState.completedCount++;
            if (progress.trigger !== BackupTrigger.MANUAL) return;

            if (progress.status === BackupTaskStatus.COMPLETED) {
                toast.success("Backup created", {description: progress.backupName});
            } else if (progress.status === BackupTaskStatus.FAILED) {
                toast.danger("Could not create backup", {description: progress.errorMessage});
            }
        });
    });
}

/** Subscribe to the progress of the recovery of images which are missing after a restore **/
export function initializeImageRecoveryState() {
    if (backupState.imageRecoverySubscription) return;

    backupState.imageRecoverySubscription = BackupEndpoint.subscribeToImageRecoveryEvents().onNext((progresses: ImageRecoveryProgress[]) => {
        progresses.forEach((progress: ImageRecoveryProgress) => {
            const previous = backupState.imageRecovery[progress.taskId];
            if (previous && previous.status !== BackupTaskStatus.IN_PROGRESS && progress.status === BackupTaskStatus.IN_PROGRESS) return;
            backupState.imageRecovery[progress.taskId] = progress;

            if (previous?.status !== BackupTaskStatus.IN_PROGRESS || progress.status === BackupTaskStatus.IN_PROGRESS) return;
            if (progress.status === BackupTaskStatus.COMPLETED) {
                toast.success("Images restored", {
                    description: `${progress.restored} restored, ${progress.removed} removed, ${progress.failed} failed`
                });
            } else if (progress.status === BackupTaskStatus.FAILED) {
                toast.danger("Could not restore images", {description: progress.errorMessage});
            }
        });
    });
}

/** Adds a backup which has just been started, so it is shown before the first update arrives **/
export function trackBackup(progress: BackupProgress) {
    if (!backupState.state[progress.backupId]) {
        backupState.state[progress.backupId] = progress;
    }
}
