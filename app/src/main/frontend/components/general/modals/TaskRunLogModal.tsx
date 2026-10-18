import React, {useEffect, useRef, useState} from "react";
import {Button, Modal, Spinner, Tooltip} from "@heroui/react";
import {ArrowUDownLeftIcon, DownloadSimpleIcon} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import {TaskEndpoint} from "Frontend/generated/endpoints";
import type TaskRunDto from "Frontend/generated/org/gameyfin/app/core/tasks/dto/TaskRunDto";
import {isRunActive, taskState} from "Frontend/state/TaskState";
import {TaskRunStatusChip} from "Frontend/components/general/tasks/TaskRunComponents";

const POLL_INTERVAL_MS = 2000;

interface TaskRunLogModalProps {
    run?: TaskRunDto;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
}

export default function TaskRunLogModal({run, isOpen, onOpenChange}: TaskRunLogModalProps) {
    const runs = useSnapshot(taskState).runs;
    // Prefer the live state of the run, so the log is refreshed as long as the run is active
    const currentRun = (run && runs[run.id] as TaskRunDto) ?? run;
    const active = !!currentRun && isRunActive(currentRun);
    const [log, setLog] = useState<string>();
    const [loading, setLoading] = useState(false);
    const [softWrap, setSoftWrap] = useState(true);
    const logEndRef = useRef<HTMLSpanElement>(null);

    useEffect(() => {
        setLog(undefined);
        setLoading(true);
    }, [isOpen, run?.id]);

    // Poll while the run is active, the final fetch after the run has finished includes the last lines
    useEffect(() => {
        if (!isOpen || !run) return;
        let cancelled = false;

        function load() {
            TaskEndpoint.getRunLog(run!.id)
                .then(content => {
                    if (!cancelled) setLog(content);
                })
                .finally(() => {
                    if (!cancelled) setLoading(false);
                });
        }

        load();
        const intervalId = active ? setInterval(load, POLL_INTERVAL_MS) : undefined;
        return () => {
            cancelled = true;
            if (intervalId) clearInterval(intervalId);
        };
    }, [isOpen, run?.id, active]);

    useEffect(() => {
        logEndRef.current?.scrollIntoView({block: "nearest"});
    }, [log]);

    // Downloads the complete log file, the dialog only shows the end of very large logs
    function downloadLog() {
        if (!run) return;
        const link = document.createElement("a");
        link.href = `/task-runs/${encodeURIComponent(run.id)}/log`;
        link.download = `task-run-${run.id}.log`;
        document.body.appendChild(link);
        link.click();
        link.remove();
    }

    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
                <Modal.Container size="lg" className="max-w-5xl">
                    <Modal.Dialog>
                        {({close}) => (
                            <>
                                <Modal.Header>
                                    <div className="flex flex-row items-center gap-2 grow">
                                        <Modal.Heading>{currentRun?.displayName ?? "Task log"}</Modal.Heading>
                                        {currentRun && <TaskRunStatusChip run={currentRun}/>}
                                        <div className="grow"/>
                                        <Tooltip delay={0}>
                                            <Tooltip.Trigger>
                                                {/* Wrapper keeps the tooltip working for disabled buttons */}
                                                <span>
                                                    <Button isIconOnly size="sm" variant="ghost"
                                                            aria-label="Download log"
                                                            isDisabled={loading || !log}
                                                            onPress={downloadLog}>
                                                        <DownloadSimpleIcon/>
                                                    </Button>
                                                </span>
                                            </Tooltip.Trigger>
                                            <Tooltip.Content placement="bottom">Download log</Tooltip.Content>
                                        </Tooltip>
                                        <Tooltip delay={0}>
                                            <Tooltip.Trigger>
                                                <Button isIconOnly size="sm"
                                                        aria-label="Soft-wrap"
                                                        onPress={() => setSoftWrap(!softWrap)}
                                                        variant={softWrap ? "primary" : "ghost"}>
                                                    <ArrowUDownLeftIcon/>
                                                </Button>
                                            </Tooltip.Trigger>
                                            <Tooltip.Content placement="bottom">Soft-wrap</Tooltip.Content>
                                        </Tooltip>
                                    </div>
                                </Modal.Header>
                                <Modal.Body>
                                    {currentRun?.errorMessage &&
                                        <p className="text-danger text-sm mb-2">{currentRun.errorMessage}</p>}
                                    <div
                                        className="flex h-[60vh] flex-col overflow-auto bg-default/40 px-2 py-1 text-sm text-default-foreground">
                                        {loading ?
                                            <div className="flex grow items-center justify-center">
                                                <Spinner/>
                                            </div> :
                                            <code
                                                className={`font-mono font-normal ${softWrap ? "whitespace-pre-wrap break-words" : "whitespace-pre"}`}>
                                                {log || "No log entries available for this run."}
                                            </code>
                                        }
                                        <span ref={logEndRef}/>
                                    </div>
                                </Modal.Body>
                                <Modal.Footer>
                                    <Button variant="ghost" onPress={close}>Close</Button>
                                </Modal.Footer>
                            </>
                        )}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}
