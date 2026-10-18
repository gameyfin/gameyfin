import React, {useEffect, useRef, useState} from "react";
import {Chip, ProgressBar} from "@heroui/react";
import type TaskRunDto from "Frontend/generated/org/gameyfin/app/core/tasks/dto/TaskRunDto";
import TaskRunStatus from "Frontend/generated/org/gameyfin/app/core/tasks/TaskRunStatus";
import TaskTrigger from "Frontend/generated/org/gameyfin/pluginapi/tasks/TaskTrigger";
import {isRunActive} from "Frontend/state/TaskState";

const statusColors: Record<TaskRunStatus, "accent" | "success" | "danger" | "warning"> = {
    [TaskRunStatus.IN_PROGRESS]: "accent",
    [TaskRunStatus.COMPLETED]: "success",
    [TaskRunStatus.FAILED]: "danger",
    [TaskRunStatus.CANCELLED]: "warning"
};

const statusLabels: Record<TaskRunStatus, string> = {
    [TaskRunStatus.IN_PROGRESS]: "Running",
    [TaskRunStatus.COMPLETED]: "Completed",
    [TaskRunStatus.FAILED]: "Failed",
    [TaskRunStatus.CANCELLED]: "Cancelled"
};

const triggerLabels: Record<TaskTrigger, string> = {
    [TaskTrigger.SCHEDULE]: "Schedule",
    [TaskTrigger.MANUAL]: "Manual",
    [TaskTrigger.SYSTEM]: "System",
    [TaskTrigger.WATCHER]: "File watcher"
};

export function triggerLabel(trigger: TaskTrigger): string {
    return triggerLabels[trigger] ?? trigger;
}

export function TaskRunStatusChip({run}: { run: TaskRunDto }) {
    const label = run.status === TaskRunStatus.IN_PROGRESS && run.cancelRequested
        ? "Cancelling"
        : statusLabels[run.status];
    return <Chip size="sm" variant="soft" color={statusColors[run.status]}>{label}</Chip>;
}

/** Shows the current step of an active run including an estimation of the remaining time */
export function TaskRunProgress({run}: { run: TaskRunDto }) {
    const [, setNow] = useState(Date.now());
    const etaCache = useRef<{ eta: string | null; computedAt: number } | undefined>(undefined);
    const active = isRunActive(run);

    useEffect(() => {
        if (!active) return;
        const intervalId = setInterval(() => setNow(Date.now()), 1000);
        return () => clearInterval(intervalId);
    }, [active]);

    function estimateTimeLeft(): string | null {
        const now = Date.now();
        // Only recompute every 5 seconds to avoid a jumping estimation
        if (etaCache.current && now - etaCache.current.computedAt < 5000) return etaCache.current.eta;

        let eta: string | null = null;
        const current = run.step?.current;
        const total = run.step?.total;
        const elapsed = (now - new Date(run.startedAt).getTime()) / 1000;
        if (current && total && current > 0 && total > 0 && elapsed > 0) {
            const secondsLeft = Math.round((total - current) / (current / elapsed));
            if (secondsLeft >= 0) {
                const mins = Math.floor(secondsLeft / 60);
                const secs = secondsLeft % 60;
                eta = `${mins}:${secs.toString().padStart(2, "0")} min left`;
            }
        }
        etaCache.current = {eta, computedAt: now};
        return eta;
    }

    if (!active) return null;

    const description = run.cancelRequested ? "Cancelling..." : (run.step?.description ?? "Running");
    const current = run.step?.current;
    const total = run.step?.total;

    if (current != null && total) {
        return (
            <div className="flex flex-col gap-1">
                <div className="flex flex-row justify-between gap-4">
                    <p className="text-muted">{`${description} (${current}/${total})`}</p>
                    <p className="text-muted">{estimateTimeLeft()}</p>
                </div>
                <ProgressBar aria-label={description} value={current / total * 100} size="sm">
                    <ProgressBar.Track>
                        <ProgressBar.Fill/>
                    </ProgressBar.Track>
                </ProgressBar>
            </div>
        );
    }

    return (
        <div className="flex flex-col gap-1">
            <p className="text-muted">{description}</p>
            <ProgressBar aria-label={description} isIndeterminate size="sm">
                <ProgressBar.Track>
                    <ProgressBar.Fill/>
                </ProgressBar.Track>
            </ProgressBar>
        </div>
    );
}
