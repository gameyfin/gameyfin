import {proxy} from 'valtio';
import {toast} from "@heroui/react";
import {Subscription} from "@vaadin/hilla-frontend";
import {TaskEndpoint} from "Frontend/generated/endpoints";
import type TaskRunDto from "Frontend/generated/org/gameyfin/app/core/tasks/dto/TaskRunDto";
import TaskRunStatus from "Frontend/generated/org/gameyfin/app/core/tasks/TaskRunStatus";

type TaskState = {
    subscription?: Subscription<TaskRunDto[]>;
    /** Active and recently finished task runs */
    runs: Record<string, TaskRunDto>;
    /** Number of runs per task which have been finished while this client was connected */
    finishedCount: Record<string, number>;
    /** Runs started by this client, which are announced by a toast once they have finished */
    tracked: Record<string, boolean>;
    isRunning: boolean;
    sortedByStartTime: TaskRunDto[];
};

export const taskState = proxy<TaskState>({
    runs: {},
    finishedCount: {},
    tracked: {},
    get isRunning(): boolean {
        return Object.values(this.runs).some((run: TaskRunDto) => run.status === TaskRunStatus.IN_PROGRESS);
    },
    get sortedByStartTime(): TaskRunDto[] {
        return Object.values(this.runs).sort((a: TaskRunDto, b: TaskRunDto) =>
            new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime());
    }
});

export function isRunActive(run: TaskRunDto): boolean {
    return run.status === TaskRunStatus.IN_PROGRESS;
}

/** Subscribe to and process state updates from backend **/
export function initializeTaskState() {
    if (taskState.subscription) return;

    taskState.subscription = TaskEndpoint.subscribeToTaskRuns().onNext((runs: TaskRunDto[]) => {
        runs.forEach(handleUpdate);
    });
}

function handleUpdate(run: TaskRunDto) {
    const previous = taskState.runs[run.id];
    // A finished run never becomes active again, so this can only be an outdated update
    if (previous && !isRunActive(previous) && isRunActive(run)) return;
    taskState.runs[run.id] = run;

    // Only count and announce runs which have been observed while running
    if (!previous || !isRunActive(previous) || isRunActive(run)) return;
    taskState.finishedCount[run.taskKey] = (taskState.finishedCount[run.taskKey] ?? 0) + 1;

    if (!taskState.tracked[run.id]) return;
    delete taskState.tracked[run.id];
    switch (run.status) {
        case TaskRunStatus.COMPLETED:
            toast.success(`${run.displayName} finished`, {description: run.summary});
            break;
        case TaskRunStatus.CANCELLED:
            toast.warning(`${run.displayName} cancelled`, {description: run.summary});
            break;
        case TaskRunStatus.FAILED:
            toast.danger(`${run.displayName} failed`, {description: run.errorMessage});
            break;
    }
}

/**
 * Adds a run which has just been started by this client, so it is shown before the first update arrives and a
 * toast is shown once it has finished.
 */
export function trackRun(run: TaskRunDto) {
    taskState.tracked[run.id] = true;
    // A fast operation may finish before its response or first subscription update arrives.
    const current = taskState.runs[run.id];
    const finished = current && !isRunActive(current) ? current : !isRunActive(run) ? run : undefined;
    if (finished) {
        taskState.runs[run.id] = {...finished, status: TaskRunStatus.IN_PROGRESS};
        handleUpdate(finished);
    } else if (!current) {
        taskState.runs[run.id] = run;
    }
}
