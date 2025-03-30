import {Button, Card, Chip, Skeleton, Tooltip, useDisclosure} from "@heroui/react";
import {
    CheckCircle,
    PauseCircle,
    PlayCircle,
    Power,
    QuestionMark,
    SlidersHorizontal,
    StopCircle,
    WarningCircle
} from "@phosphor-icons/react";
import {PluginManagementEndpoint} from "Frontend/generated/endpoints";
import PluginDto from "Frontend/generated/de/grimsi/gameyfin/core/plugins/management/PluginDto";
import PluginState from "Frontend/generated/org/pf4j/PluginState";
import React, {ReactNode, useEffect, useState} from "react";
import PluginDetailsModal from "Frontend/components/general/PluginDetailsModal";
import PluginLogo from "Frontend/components/general/PluginLogo";

export function PluginManagementCard({plugin, updatePlugin}: {
    plugin: PluginDto,
    updatePlugin: (plugin: PluginDto) => void
}) {
    const pluginDetailsModal = useDisclosure();
    const [configValid, setConfigValid] = useState<boolean | undefined>(undefined);

    useEffect(() => {
        PluginManagementEndpoint.validatePluginConfig(plugin.id).then((response: boolean) => {
            if (response === undefined) return;
            setConfigValid(response);
        });
    }, [pluginDetailsModal.isOpen]);

    function borderColor(state: PluginState | undefined): "success" | "warning" | "danger" | "default" {
        if (isDisabled(state)) return "warning";
        if (configValid === undefined) return "default";
        if (!configValid) return "danger";
        return stateToColor(state);
    }

    function stateToColor(state: PluginState | undefined): "success" | "warning" | "danger" | "default" {
        switch (state) {
            case PluginState.STARTED:
                return "success";
            case PluginState.DISABLED:
                return "warning";
            case PluginState.FAILED:
                return "danger";
            default:
                return "default";
        }
    }

    function stateToIcon(state: PluginState | undefined): ReactNode {
        switch (state) {
            case PluginState.STARTED:
                return <PlayCircle/>;
            case PluginState.DISABLED:
                return <PauseCircle/>;
            case PluginState.FAILED:
                return <StopCircle/>;
            default:
                return <QuestionMark/>;
        }
    }

    function isDisabled(state: PluginState | undefined): boolean {
        return state === PluginState.DISABLED;
    }

    function togglePluginEnabled() {
        if (isDisabled(plugin.state)) {
            PluginManagementEndpoint.enablePlugin(plugin.id).then(() => {
                PluginManagementEndpoint.getPlugin(plugin.id).then((response) => {
                    if (response === undefined) return;
                    updatePlugin(response);
                });
            });
        } else {
            PluginManagementEndpoint.disablePlugin(plugin.id).then(() => {
                PluginManagementEndpoint.getPlugin(plugin.id).then((response) => {
                    if (response === undefined) return;
                    updatePlugin(response);
                });
            });
        }
    }

    // @ts-ignore
    return (
        <>
            <Card className={`flex flex-row justify-between p-2 border-2 border-${borderColor(plugin.state)}`}>
                <div className="absolute right-0 top-0 flex flex-row">
                    <Tooltip content={`${isDisabled(plugin.state) ? "Enable" : "Disable"} plugin`} placement="bottom"
                             color="foreground">
                        <Button isIconOnly variant="light" onPress={() => togglePluginEnabled()}>
                            <Power/>
                        </Button>
                    </Tooltip>
                    <Tooltip content="Configuration" placement="bottom" color="foreground">
                        <Button isIconOnly variant="light" onPress={pluginDetailsModal.onOpen}>
                            <SlidersHorizontal/>
                        </Button>
                    </Tooltip>
                </div>
                <div className="flex flex-1 flex-col items-center gap-1">
                    <PluginLogo plugin={plugin}/>
                    <p className="font-semibold">{plugin.name}</p>
                    <div className="flex flex-row gap-2">
                        <Chip size="sm" radius="sm" className="text-xs">{plugin.version}</Chip>
                        <Chip size="sm" radius="sm" className="text-xs" color={stateToColor(plugin.state)}>
                            <Tooltip content={`Plugin ${plugin.state?.toLowerCase()}`} placement="bottom"
                                     color="foreground">
                                {stateToIcon(plugin.state)}
                            </Tooltip>
                        </Chip>
                        {configValid === undefined ?
                            <Skeleton className="rounded-md h-6 w-9"/>
                            : configValid ?
                                <Tooltip content="Config valid" placement="bottom" color="foreground">
                                    <Chip size="sm" radius="sm" className="text-xs" color="success">
                                        <CheckCircle/>
                                    </Chip>
                                </Tooltip> :
                                <Tooltip content="Config invalid" placement="bottom" color="foreground">
                                    <Chip size="sm" radius="sm" className="text-xs" color="danger">
                                        <WarningCircle/>
                                    </Chip>
                                </Tooltip>
                        }
                    </div>
                </div>
            </Card>
            <PluginDetailsModal plugin={plugin}
                                isOpen={pluginDetailsModal.isOpen}
                                onOpenChange={pluginDetailsModal.onOpenChange}
                                updatePlugin={updatePlugin}
            />
        </>

    )
}