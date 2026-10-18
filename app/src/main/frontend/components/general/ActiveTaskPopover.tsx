import React, {useState} from "react";
import {Button, Link, Popover, ScrollShadow, Separator, Spinner, toast, Tooltip, useOverlayState} from "@heroui/react";
import {useSnapshot} from "valtio/react";
import {FileTextIcon, ListChecksIcon, StopIcon, WarningIcon} from "@phosphor-icons/react";
import {TaskEndpoint} from "Frontend/generated/endpoints";
import type TaskRunDto from "Frontend/generated/org/gameyfin/app/core/tasks/dto/TaskRunDto";
import TaskRunStatus from "Frontend/generated/org/gameyfin/app/core/tasks/TaskRunStatus";
import {isRunActive, taskState} from "Frontend/state/TaskState";
import {timeBetween, timeUntil} from "Frontend/util/utils";
import {TaskRunProgress, TaskRunStatusChip} from "Frontend/components/general/tasks/TaskRunComponents";
import TaskRunLogModal from "Frontend/components/general/modals/TaskRunLogModal";

/** Quick overview of active and recently finished task runs */
export default function ActiveTaskPopover() {
    const state = useSnapshot(taskState);
    const runs = state.sortedByStartTime as TaskRunDto[];
    const [selectedRun, setSelectedRun] = useState<TaskRunDto>();
    const logModal = useOverlayState();

    async function cancelRun(run: TaskRunDto) {
        try {
            await TaskEndpoint.cancelRun(run.id);
        } catch (e: any) {
            toast.danger("Could not cancel task", {description: e?.message});
        }
    }

    function result(run: TaskRunDto) {
        const duration = run.finishedAt ? ` (in ${timeBetween(run.startedAt, run.finishedAt)})` : "";
        switch (run.status) {
            case TaskRunStatus.COMPLETED:
                return <p>{run.summary ?? "Completed"}{duration}</p>;
            case TaskRunStatus.CANCELLED:
                return <p className="text-warning">Cancelled{run.summary ? `: ${run.summary}` : ""}{duration}</p>;
            case TaskRunStatus.FAILED:
                return <p className="text-danger flex flex-row items-center gap-1">
                    <WarningIcon weight="fill" className="shrink-0"/>
                    {run.errorMessage ?? "Failed (check the log for details)"}
                </p>;
            default:
                return <TaskRunProgress run={run}/>;
        }
    }

    return (
        <>
            <Popover>
                <Button isIconOnly variant="ghost" aria-label="Active tasks">
                    {state.isRunning ?
                        <Spinner size="sm" color="current"/> :
                        <ListChecksIcon className="fill-muted"/>
                    }
                </Button>
                <Popover.Content placement="bottom end">
                    <Popover.Dialog>
                        <Popover.Arrow/>
                        <div className="flex flex-col gap-2 m-2 min-w-md max-w-xl">
                            {runs.length === 0 ?
                                <p className="flex h-12 items-center justify-center text-sm text-muted">
                                    No tasks in progress or in history.
                                </p> :
                                <ScrollShadow hideScrollBar className="max-h-96">
                                    {runs.map((run, index) =>
                                        <div className="flex flex-col" key={run.id}>
                                            <div className="flex flex-row gap-4 justify-between items-center mb-1">
                                                <div className="flex flex-row items-center gap-2 min-w-0">
                                                    <TaskRunStatusChip run={run}/>
                                                    {run.link ?
                                                        <Link className="underline text-sm truncate" href={run.link}>
                                                            {run.displayName}
                                                        </Link> :
                                                        <p className="truncate">{run.displayName}</p>
                                                    }
                                                </div>
                                                <div className="flex flex-row items-center gap-1 shrink-0">
                                                    <p className="text-muted">
                                                        {run.finishedAt ?
                                                            `Finished ${timeUntil(run.finishedAt)}` :
                                                            `Started ${timeUntil(run.startedAt)}`}
                                                    </p>
                                                    <Tooltip delay={0}>
                                                        <Tooltip.Trigger>
                                                            <Button isIconOnly size="sm" variant="ghost"
                                                                    aria-label="Show log"
                                                                    onPress={() => {
                                                                        setSelectedRun(run);
                                                                        logModal.open();
                                                                    }}>
                                                                <FileTextIcon/>
                                                            </Button>
                                                        </Tooltip.Trigger>
                                                        <Tooltip.Content>Show log</Tooltip.Content>
                                                    </Tooltip>
                                                    {isRunActive(run) && run.cancellable &&
                                                        <Tooltip delay={0}>
                                                            <Tooltip.Trigger>
                                                                <Button isIconOnly size="sm" variant="ghost"
                                                                        aria-label="Cancel"
                                                                        isDisabled={run.cancelRequested}
                                                                        onPress={() => cancelRun(run)}>
                                                                    <StopIcon className="fill-danger"/>
                                                                </Button>
                                                            </Tooltip.Trigger>
                                                            <Tooltip.Content>Cancel</Tooltip.Content>
                                                        </Tooltip>}
                                                </div>
                                            </div>
                                            <div className="text-muted">{result(run)}</div>
                                            {index < runs.length - 1 && <Separator className="my-2"/>}
                                        </div>
                                    )}
                                </ScrollShadow>
                            }
                            <Separator/>
                            <Link className="underline text-sm self-end" href="/administration/tasks">
                                Manage tasks
                            </Link>
                        </div>
                    </Popover.Dialog>
                </Popover.Content>
            </Popover>
            <TaskRunLogModal run={selectedRun} isOpen={logModal.isOpen} onOpenChange={logModal.setOpen}/>
        </>
    );
}
