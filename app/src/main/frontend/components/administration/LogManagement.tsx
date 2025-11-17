import React, {useEffect, useRef, useState} from "react";
import {LogEndpoint} from "Frontend/generated/endpoints";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import * as Yup from 'yup';
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import {addToast, Button, Code, Divider, Tooltip} from "@heroui/react";
import { ArrowUDownLeftIcon, SortAscendingIcon } from "@phosphor-icons/react";

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
                .catch(() => addToast({
                    title: "Error",
                    description: "Failed to apply log configuration",
                    color: "danger"
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
                        <Tooltip content="Soft-wrap" placement="bottom">
                            <Button isIconOnly
                                    onPress={() => setSoftWrap(!softWrap)}
                                    variant={softWrap ? "solid" : "ghost"}
                            >
                                <ArrowUDownLeftIcon/>
                            </Button>
                        </Tooltip>
                        <Tooltip content="Auto-scroll" placement="bottom">
                            <Button isIconOnly
                                    onPress={() => setAutoScroll(!autoScroll)}
                                    variant={autoScroll ? "solid" : "ghost"}
                            >
                                <SortAscendingIcon/>
                            </Button>
                        </Tooltip>
                    </div>
                </div>
                <Divider className="mb-4"/>
            </div>
            <Code size="sm" radius="none"
                  className={`flex flex-col h-[50vh] max-h-[50vh] text-sm overflow-auto ${softWrap ? "whitespace-normal break-words" : "whitespace-nowrap"}`}>
                {logEntries.map((entry, index) => <p key={index}>{entry}</p>)}
                <div ref={logEndRef}/>
            </Code>
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