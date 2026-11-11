import CompactRecordList from "Frontend/components/layout/CompactRecordList";
import {COMPACT_QUERY, useMediaQuery} from "Frontend/util/useMediaQuery";
import TableScroller from "Frontend/components/layout/TableScroller";
import React, {useEffect, useMemo, useState} from "react";
import {Button, Chip, Label, ListBox, Select, Spinner, Table, toast, Tooltip, useOverlayState} from "@heroui/react";
import {ClockCounterClockwiseIcon, FileTextIcon, GearIcon, PlayIcon, StopIcon} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import {TaskEndpoint} from "Frontend/generated/endpoints";
import type TaskDto from "Frontend/generated/org/gameyfin/app/core/tasks/dto/TaskDto";
import type TaskRunDto from "Frontend/generated/org/gameyfin/app/core/tasks/dto/TaskRunDto";
import TaskSource from "Frontend/generated/org/gameyfin/app/core/tasks/TaskSource";
import {isRunActive, taskState, trackRun} from "Frontend/state/TaskState";
import {timeBetween, timeUntil} from "Frontend/util/utils";
import {TaskRunProgress, TaskRunStatusChip, triggerLabel} from "Frontend/components/general/tasks/TaskRunComponents";
import TaskEditModal from "Frontend/components/general/modals/TaskEditModal";
import TaskRunLogModal from "Frontend/components/general/modals/TaskRunLogModal";
import TaskRunParametersModal from "Frontend/components/general/modals/TaskRunParametersModal";

const PAGE_SIZE = 25;
const ALL_TASKS = "__all__";

export function TaskManagement() {
    const compact = useMediaQuery(COMPACT_QUERY);
    const [tasks, setTasks] = useState<TaskDto[]>();
    const [history, setHistory] = useState<TaskRunDto[]>([]);
    const [hasMore, setHasMore] = useState(false);
    const [page, setPage] = useState(0);
    const [loadingHistory, setLoadingHistory] = useState(false);
    const [filter, setFilter] = useState<string>(ALL_TASKS);
    const [starting, setStarting] = useState<Record<string, boolean>>({});
    const [selectedTask, setSelectedTask] = useState<TaskDto>();
    const [selectedRun, setSelectedRun] = useState<TaskRunDto>();
    const [parameterTask, setParameterTask] = useState<TaskDto>();
    const editModal = useOverlayState();
    const parametersModal = useOverlayState();
    const logModal = useOverlayState();

    const liveState = useSnapshot(taskState);
    const liveRuns = liveState.runs as Record<string, TaskRunDto>;
    const activeRunIds = Object.values(liveRuns).filter(isRunActive).map(run => run.id).sort().join(",");
    const finishedCount = Object.values(liveState.finishedCount).reduce((a, b) => a + b, 0);

    // Refresh tasks whenever a run has been started or finished, so running state, last and next runs are up-to-date
    useEffect(() => {
        loadTasks();
    }, [activeRunIds, finishedCount]);

    useEffect(() => {
        loadHistory(0, true);
    }, [filter]);

    async function loadTasks() {
        setTasks(await TaskEndpoint.getTasks());
    }

    async function loadHistory(pageToLoad: number, reset: boolean) {
        setLoadingHistory(true);
        try {
            const result = await TaskEndpoint.getRuns(filter === ALL_TASKS ? undefined : filter, pageToLoad, PAGE_SIZE);
            setHistory(current => reset ? result.runs : [...current, ...result.runs.filter(r => !current.some(c => c.id === r.id))]);
            setHasMore(result.hasMore);
            setPage(pageToLoad);
        } finally {
            setLoadingHistory(false);
        }
    }

    // Merge live updates into the loaded history and prepend runs which have been started in the meantime
    const runs = useMemo(() => {
        const loadedIds = new Set(history.map(run => run.id));
        const oldestLoaded = history.length > 0 ? new Date(history[history.length - 1].startedAt).getTime() : 0;
        const newRuns = Object.values(liveRuns).filter(run =>
            run.visibleInOverview !== false &&
            !loadedIds.has(run.id) &&
            (filter === ALL_TASKS || run.taskKey === filter) &&
            (!hasMore || new Date(run.startedAt).getTime() >= oldestLoaded));
        return [...newRuns, ...history.map(run => liveRuns[run.id] ?? run)]
            .sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime());
    }, [history, liveRuns, filter, hasMore]);

    function requestRun(task: TaskDto) {
        if (task.parametersMetadata.length > 0) {
            setParameterTask(task);
            parametersModal.open();
        } else {
            runNow(task);
        }
    }

    async function runNow(task: TaskDto, parameters?: Record<string, string | undefined>) {
        setStarting(current => ({...current, [task.key]: true}));
        try {
            const run = await TaskEndpoint.runNow(task.key, parameters);
            if (run) {
                trackRun(run);
                toast.info(`${task.name} started`, {description: "The progress is shown in the task overview at the top"});
            } else {
                toast.info(`${task.name} triggered`, {description: "The progress is shown in the task overview at the top"});
            }
        } catch (e: any) {
            toast.danger(`Could not start ${task.name}`, {description: e?.message});
        } finally {
            setStarting(current => ({...current, [task.key]: false}));
        }
    }

    async function cancelRun(run: TaskRunDto) {
        try {
            await TaskEndpoint.cancelRun(run.id);
        } catch (e: any) {
            toast.danger("Could not cancel task", {description: e?.message});
        }
    }

    function showLog(run: TaskRunDto) {
        setSelectedRun(run);
        logModal.open();
    }

    function scheduleDescription(task: TaskDto) {
        if (!task.schedulable) return <span className="text-muted">Not schedulable</span>;
        const cron = task.schedule ?? task.defaultSchedule;
        if (!task.scheduleEnabled || !cron) return <span className="text-muted">Disabled</span>;
        return <code className="text-sm">{cron}</code>;
    }

    function taskFields(task: TaskDto) {
        return [
            {label: "Task", tableClassName: "", content: <>
                <div className="flex flex-col">
                    <div className="flex flex-row items-center gap-2">
                        <span className="font-semibold">{task.name}</span>
                        {task.source === TaskSource.PLUGIN &&
                            <Chip size="sm" variant="soft">
                                {task.pluginName ?? task.pluginId}
                            </Chip>}
                    </div>
                    <span className="text-sm text-muted">{task.description}</span>
                </div>
            </>},
            {label: "Schedule", tableClassName: "whitespace-nowrap", content: <>
                {scheduleDescription(task)}
            </>},
            {label: "Next run", tableClassName: "whitespace-nowrap", content: <>
                {task.nextExecution ?
                <Tooltip delay={500}>
                    <Tooltip.Trigger>
                        <span>{timeUntil(task.nextExecution)}</span>
                    </Tooltip.Trigger>
                    <Tooltip.Content>
                        {new Date(task.nextExecution).toLocaleString()}
                    </Tooltip.Content>
                </Tooltip> :
                <span className="text-muted">-</span>}
            </>},
            {label: "Last run", tableClassName: "whitespace-nowrap", content: <>
                {task.lastRun ?
                <div className="flex flex-row items-center gap-2">
                    <TaskRunStatusChip run={liveRuns[task.lastRun.id] ?? task.lastRun}/>
                    <span className="text-sm text-muted">
                        {timeUntil(task.lastRun.finishedAt ?? task.lastRun.startedAt)}
                    </span>
                </div> :
                <span className="text-muted">Never</span>}
            </>},
            {label: "Actions", tableClassName: "", content: <>
                <div className="flex flex-wrap gap-2">
                    {task.manuallyTriggerable &&
                        <Tooltip>
                            <Button isIconOnly size="sm" variant="ghost"
                                        aria-label="Run now"
                                        isPending={starting[task.key]}
                                        onPress={() => requestRun(task)}>
                                    {starting[task.key] ?
                                        <Spinner size="sm" color="current"/> :
                                        <PlayIcon/>}
                                </Button>
                            <Tooltip.Content>Run now</Tooltip.Content>
                        </Tooltip>}
                    {(task.schedulable || task.settingsMetadata.length > 0) &&
                        <Tooltip>
                            <Button isIconOnly size="sm" variant="ghost"
                                        aria-label="Configure"
                                        onPress={() => {
                                            setSelectedTask(task);
                                            editModal.open();
                                        }}>
                                    <GearIcon/>
                                </Button>
                            <Tooltip.Content>Configure</Tooltip.Content>
                        </Tooltip>}
                    <Tooltip>
                        <Button isIconOnly size="sm" variant="ghost"
                                    aria-label="Show history"
                                    onPress={() => setFilter(task.key)}>
                                <ClockCounterClockwiseIcon/>
                            </Button>
                        <Tooltip.Content>Show history</Tooltip.Content>
                    </Tooltip>
                </div>
            </>}
        ];
    }

    function runFields(run: TaskRunDto) {
        return [
            {label: "Run", tableClassName: "", content: <>
                <div className="flex flex-col">
                    <span>{run.displayName}</span>
                    {run.displayName !== run.taskName &&
                        <span className="text-sm text-muted">{run.taskName}</span>}
                </div>
            </>},
            {label: "Trigger", tableClassName: "", content: <>
                {triggerLabel(run.trigger)}
            </>},
            {label: "Status", tableClassName: "", content: <>
                <TaskRunStatusChip run={run}/>
            </>},
            {label: "Started", tableClassName: "", content: <>
                {new Date(run.startedAt).toLocaleString()}
            </>},
            {label: "Duration", tableClassName: "", content: <>
                {run.finishedAt ? timeBetween(run.startedAt, run.finishedAt) :
                <span className="text-muted">-</span>}
            </>},
            {label: "Result", tableClassName: "max-w-md", content: <>
                {isRunActive(run) ? <TaskRunProgress run={run}/> :
                run.errorMessage ?
                    <span className="text-danger text-sm">{run.errorMessage}</span> :
                    <span className="text-sm">{run.summary}</span>}
            </>},
            {label: "Actions", tableClassName: "", content: <>
                <div className="flex flex-wrap gap-2">
                    <Tooltip>
                        <Button isIconOnly size="sm" variant="ghost"
                                    aria-label="Show log"
                                    onPress={() => showLog(run)}>
                                <FileTextIcon/>
                            </Button>
                        <Tooltip.Content>Show log</Tooltip.Content>
                    </Tooltip>
                    {isRunActive(run) && run.cancellable &&
                        <Tooltip>
                            <Button isIconOnly size="sm" variant="danger"
                                        aria-label="Cancel"
                                        isDisabled={run.cancelRequested}
                                        onPress={() => cancelRun(run)}>
                                    <StopIcon/>
                                </Button>
                            <Tooltip.Content>Cancel</Tooltip.Content>
                        </Tooltip>}
                </div>
            </>}
        ];
    }

    return (
        <div className="flex flex-col">
            <h1 className="text-2xl font-bold">Tasks</h1>
            <p className="text-sm text-muted mt-1 mb-4">
                Background tasks of Gameyfin and its plugins. Configure their schedule and settings, start them
                manually and inspect the logs of previous runs.
            </p>
            {compact ? <CompactRecordList label="Tasks" items={tasks ?? []} itemKey={task => task.key} fields={taskFields} empty={tasks ? <p>No tasks available.</p> : <Spinner aria-label="Loading tasks"/>} /> : <Table variant="secondary">
                <TableScroller label="Tasks">
                    <Table.Content aria-label="Tasks">
                        <Table.Header>
                            <Table.Column id="name" isRowHeader>Task</Table.Column>
                            <Table.Column id="schedule">Schedule</Table.Column>
                            <Table.Column id="next">Next run</Table.Column>
                            <Table.Column id="last">Last run</Table.Column>
                            <Table.Column id="actions">Actions</Table.Column>
                        </Table.Header>
                        <Table.Body items={(tasks ?? []).map(task => ({key: task.key, task}))}
                                    renderEmptyState={() => <div className="flex justify-center p-4">
                                        {tasks ? <p className="text-muted">No tasks available.</p> : <Spinner/>}
                                    </div>}>
                            {({task}) => <Table.Row key={task.key}>
                                {taskFields(task).map(field => <Table.Cell key={field.label} className={field.tableClassName}>
                                    {field.content}
                                </Table.Cell>)}
                            </Table.Row>}
                        </Table.Body>
                    </Table.Content>
                </TableScroller>
            </Table>}

            <div className="flex flex-col sm:flex-row sm:items-end justify-between mt-8 mb-1 gap-4">
                <h2 className="text-xl font-bold">Run history</h2>
                <Select className="w-full sm:w-64"
                        aria-label="Filter by task"
                        value={filter}
                        onChange={(value) => setFilter((value as string) ?? ALL_TASKS)}>
                    <Label className="sr-only">Filter by task</Label>
                    <Select.Trigger>
                        <Select.Value/>
                        <Select.Indicator/>
                    </Select.Trigger>
                    <Select.Popover>
                        <ListBox>
                            <ListBox.Item key={ALL_TASKS} id={ALL_TASKS} textValue="All tasks">
                                All tasks
                                <ListBox.ItemIndicator/>
                            </ListBox.Item>
                            {(tasks ?? []).map(task => (
                                <ListBox.Item key={task.key} id={task.key} textValue={task.name}>
                                    {task.name}
                                    <ListBox.ItemIndicator/>
                                </ListBox.Item>
                            ))}
                        </ListBox>
                    </Select.Popover>
                </Select>
            </div>
            {compact ? <CompactRecordList label="Task runs" items={runs} itemKey={run => run.id} fields={runFields} empty={loadingHistory ? <Spinner aria-label="Loading history"/> : <p>No task runs available.</p>} /> : <Table variant="secondary" className="mt-3">
                <TableScroller label="Tasks">
                    <Table.Content aria-label="Task runs">
                        <Table.Header>
                            <Table.Column id="name" isRowHeader>Run</Table.Column>
                            <Table.Column id="trigger">Trigger</Table.Column>
                            <Table.Column id="status">Status</Table.Column>
                            <Table.Column id="started">Started</Table.Column>
                            <Table.Column id="duration">Duration</Table.Column>
                            <Table.Column id="result">Result</Table.Column>
                            <Table.Column id="actions">Actions</Table.Column>
                        </Table.Header>
                        <Table.Body items={runs.map(run => ({key: run.id, run}))}
                                    renderEmptyState={() =>
                                        <div className="flex justify-center p-4">
                                            {loadingHistory ? <Spinner/> :
                                                <p className="text-muted">No task runs available.</p>}
                                        </div>}>
                            {({run}) => (
                                <Table.Row key={run.id}>
                                    {runFields(run).map(field => <Table.Cell key={field.label} className={field.tableClassName}>
                                        {field.content}
                                    </Table.Cell>)}
                                </Table.Row>
                            )}
                        </Table.Body>
                    </Table.Content>
                </TableScroller>
            </Table>}
            {hasMore &&
                <div className="flex justify-center mt-4">
                    <Button variant="ghost"
                            isPending={loadingHistory}
                            onPress={() => loadHistory(page + 1, false)}>
                        {loadingHistory && <Spinner size="sm" color="current"/>}
                        Load more
                    </Button>
                </div>}

            {selectedTask &&
                <TaskEditModal task={selectedTask}
                               isOpen={editModal.isOpen}
                               onOpenChange={editModal.setOpen}
                               onSaved={updated =>
                                   setTasks(current => current?.map(task => task.key === updated.key ? updated : task))}/>}
            {parameterTask &&
                <TaskRunParametersModal task={parameterTask}
                                        isOpen={parametersModal.isOpen}
                                        onOpenChange={parametersModal.setOpen}
                                        onRun={runNow}/>}
            <TaskRunLogModal run={selectedRun}
                             isOpen={logModal.isOpen}
                             onOpenChange={logModal.setOpen}/>
        </div>
    );
}
