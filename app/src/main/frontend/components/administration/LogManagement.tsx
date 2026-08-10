import React, {useEffect, useRef, useState} from "react";
import {LogEndpoint} from "Frontend/generated/endpoints";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import * as Yup from 'yup';
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import {toast, Button, Separator, Tooltip} from "@heroui/react";
import {ArrowUDownLeftIcon, SortAscendingIcon} from "@phosphor-icons/react";

function LogManagementLayout({getConfig, formik}: any) {
    const [logEntries, setLogEntries] = useState<string[]>([]);
    const [autoScroll, setAutoScroll] = useState(true);
    const [softWrap, setSoftWrap] = useState(false);
    const logEndRef = useRef<null | HTMLDivElement>(null);

    useEffect(() => {
        const sub = LogEndpoint.getApplicationLogs().onNext((newEntry: string | undefined) =>
            setLogEntries((currentEntries) => [...currentEntries, newEntry as string])
        );

        return () => sub.cancel();
    }, []);

    useEffect(() => {
        if (formik.isSubmitting == false && formik.submitCount > 0) {
            LogEndpoint.reloadLogConfig()
                .catch(() => toast.danger("Error", {
                    description: "Failed to apply log configuration"
                }));
        }
    }, [formik.isSubmitting]);

    useEffect(() => {
        if (autoScroll) {
            scrollToBottom();
        }
    }, [logEntries, autoScroll, softWrap]);

    function scrollToBottom() {
        logEndRef.current?.scrollIntoView();
    }

    return (
        <div className="flex flex-col mt-4">
            <div className="flex flex-row gap-4">
                <ConfigFormField configElement={getConfig("logs.folder")}/>
                <ConfigFormField configElement={getConfig("logs.max-history-days")}/>
                <ConfigFormField configElement={getConfig("logs.level.gameyfin")}/>
                <ConfigFormField configElement={getConfig("logs.level.root")}/>
            </div>

            <div className="flex flex-col">
                <div className="flex flex-row grow justify-between items-baseline">
                    <h2 className={"text-xl font-bold mt-8 mb-1"}>Application logs</h2>
                    <div className="flex flex-row gap-1">
                        <Tooltip delay={0}>
                            <Tooltip.Trigger>
                                <Button isIconOnly
                                        onPress={() => setSoftWrap(!softWrap)}
                                        variant={softWrap ? "primary" : "ghost"}
                                >
                                    <ArrowUDownLeftIcon/>
                                </Button>
                            </Tooltip.Trigger>
                            <Tooltip.Content placement="bottom">
                                <p>Soft-wrap</p>
                            </Tooltip.Content>
                        </Tooltip>
                        <Tooltip delay={0}>
                            <Tooltip.Trigger>
                                <Button isIconOnly
                                        onPress={() => setAutoScroll(!autoScroll)}
                                        variant={autoScroll ? "primary" : "ghost"}
                                >
                                    <SortAscendingIcon/>
                                </Button>
                            </Tooltip.Trigger>
                            <Tooltip.Content placement="bottom">
                                <p>Auto-scroll</p>
                            </Tooltip.Content>
                        </Tooltip>
                    </div>
                </div>
                <Separator className="mb-4"/>
            </div>
            <div
                className="flex h-[50vh] max-h-[50vh] flex-col overflow-auto rounded-none bg-default/40 px-2 py-1 text-sm text-default-foreground">
                <code className={`font-mono font-normal ${softWrap ? "whitespace-pre-wrap break-words" : "whitespace-pre"}`}>
                    {logEntries.join("\n")}
                </code>
                <span ref={logEndRef}/>
            </div>
        </div>
    );
}

const validationSchema = Yup.object({
    logs: Yup.object({
        folder: Yup.string().required("Required"),
        "max-history-days": Yup.number().required("Required"),
        level: Yup.object({
            gameyfin: Yup.string().required("Required"),
            root: Yup.string().required("Required")
        })
    })
});

export const LogManagement = withConfigPage(LogManagementLayout, "Logging", validationSchema);