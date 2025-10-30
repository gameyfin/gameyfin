import React from "react";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import Section from "Frontend/components/general/Section";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import * as Yup from "yup";
import {Alert, Button, Card, Chip, Tooltip} from "@heroui/react";
import {FlaskIcon, InfoIcon, SpeedometerIcon} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import {downloadSessionState} from "Frontend/state/DownloadSessionState";
import SessionStatsDto from "Frontend/generated/org/gameyfin/app/core/download/bandwidth/SessionStatsDto";
import {gameState} from "Frontend/state/GameState";
import ChipList from "Frontend/components/general/ChipList";

function DownloadManagementLayout({getConfig, formik}: any) {
    const activeSessions = useSnapshot(downloadSessionState).active as SessionStatsDto[];
    const games = useSnapshot(gameState).state;

    return (
        <div className="flex flex-col">
            <Alert
                title="Experimental Feature"
                description="Bandwidth limiting is an experimental feature and may not work as expected. Please report any issues you encounter."
                variant="solid"
                hideIconWrapper={true}
                icon={<FlaskIcon size={24}/>}
                endContent={
                    <Button variant="flat"
                            className="bg-default-300"
                            onPress={() => window.open("https://github.com/gameyfin/gameyfin/issues", "_blank")}>
                        Open Issues
                    </Button>

                }
                classNames={{
                    title: "font-bold",
                    base: "mt-6"
                }}
            />
            <Section title="Bandwidth limiting"/>
            <div className="flex flex-col gap-4">
                <div className="flex flex-row items-baseline gap-4">
                    <ConfigFormField configElement={getConfig("downloads.bandwidth-limit.enabled")}/>
                    <ConfigFormField configElement={getConfig("downloads.bandwidth-limit.mbps")}
                                     isDisabled={!formik.values.downloads["bandwidth-limit"].enabled}/>
                </div>
            </div>
            <Section title="Live view"/>
            {activeSessions.length === 0 &&
                <p className="text-center text-default-500">No active download sessions.</p>
            }
            <div className="flex flex-col gap-2">
                {activeSessions.map((session: SessionStatsDto) =>
                    <Card className="flex flex-col gap-2 p-4">
                        <div className="flex flex-row justify-between items-center">
                            <p className="flex flex-row items-center">
                                <b>User:</b>&nbsp;
                                {session.username ?? "Anonymous User"}&nbsp;
                                <Tooltip
                                    content={<pre>{session.sessionId}</pre>}
                                    placement="right"
                                >
                                    <InfoIcon size={18}/>
                                </Tooltip>
                            </p>
                            <p>Remote IP:&nbsp;
                                {<Chip size="sm"
                                       radius="sm">
                                    <pre>{session.remoteIp}</pre>
                                </Chip>}
                            </p>
                        </div>
                        <div className="flex flex-row gap-4 justify-between items-center">
                            <div className="flex flex-row gap-2">
                                Active downloads:
                                <ChipList items={session.activeGameIds.map(gameId => games[gameId].title)}/>
                            </div>
                            <Card
                                className={`flex flex-col gap-2 items-center aspect-1/1 ${session.currentMbps > 0 ? 'bg-success-100 text-success-300' : 'bg-default'}`}>
                                <SpeedometerIcon size={128}
                                                 className={session.currentMbps > 0 ? 'fill-success-300' : 'fill-default'}/>
                                <p>{session.currentMbps.toFixed(1)} Mb/s</p>
                            </Card>
                        </div>
                    </Card>
                )}
            </div>
        </div>
    );
}

const validationSchema = Yup.object({
    downloads: Yup.object({
        "bandwidth-limit": Yup.object({
            enabled: Yup.boolean().required("Required"),
            mbps: Yup.number()
                .min(1, "Must be at least 1 Mbps")
                .required("Required"),
        }).required("Required")
    })
});
export const DownloadManagement = withConfigPage(DownloadManagementLayout, "Downloads", validationSchema);