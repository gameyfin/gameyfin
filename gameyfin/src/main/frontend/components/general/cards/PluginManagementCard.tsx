import {Button, Card, Chip, Skeleton, Tooltip, useDisclosure} from "@heroui/react";
import {
    CheckCircle,
    IconContext,
    PauseCircle,
    PlayCircle,
    Power,
    Question,
    QuestionMark,
    SealCheck,
    SealQuestion,
    SealWarning,
    SlidersHorizontal,
    StopCircle,
    WarningCircle,
    XCircle
} from "@phosphor-icons/react";
import {PluginManagementEndpoint} from "Frontend/generated/endpoints";
import PluginDto from "Frontend/generated/de/grimsi/gameyfin/core/plugins/management/PluginDto";
import PluginState from "Frontend/generated/org/pf4j/PluginState";
import React, {ReactNode, useEffect, useState} from "react";
import PluginDetailsModal from "Frontend/components/general/modals/PluginDetailsModal";
import PluginLogo from "Frontend/components/general/PluginLogo";
import PluginTrustLevel from "Frontend/generated/de/grimsi/gameyfin/core/plugins/management/PluginTrustLevel";
import PluginConfigValidationResult
    from "Frontend/generated/de/grimsi/gameyfin/core/plugins/config/PluginConfigValidationResult";

export function PluginManagementCard({plugin, updatePlugin}: {
    plugin: PluginDto,
    updatePlugin: (plugin: PluginDto) => void
}) {
    const pluginDetailsModal = useDisclosure();
    const [configValidationResult, setConfigValidationResult] = useState<PluginConfigValidationResult | undefined>(undefined);

    useEffect(() => {
        PluginManagementEndpoint.validatePluginConfig(plugin.id).then((response: PluginConfigValidationResult | undefined) => {
            if (response === undefined) return;
            setConfigValidationResult(response);
        });
    }, [pluginDetailsModal.isOpen]);

    function borderColor(state: PluginState | undefined, trustLevel: PluginTrustLevel | undefined): "success" | "warning" | "danger" | "default" {
        if (trustLevel === PluginTrustLevel.UNTRUSTED) return "danger";

        if (isDisabled(state)) return "warning";
        if (configValidationResult === undefined) return "default";
        if (!configValidationResult) return "danger";
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
            case PluginState.UNLOADED:
            case PluginState.RESOLVED:
                return <XCircle/>;
            default:
                return <QuestionMark/>;
        }
    }

    function configValidationResultToChip(validationResult: PluginConfigValidationResult | undefined): ReactNode {
        switch (validationResult) {
            case PluginConfigValidationResult.VALID:
                return <Tooltip content="Config valid" placement="bottom" color="foreground">
                    <Chip size="sm" radius="sm" className="text-xs" color="success">
                        <CheckCircle/>
                    </Chip>
                </Tooltip>
            case PluginConfigValidationResult.INVALID:
                return <Tooltip content="Config invalid" placement="bottom" color="foreground">
                    <Chip size="sm" radius="sm" className="text-xs" color="danger">
                        <WarningCircle/>
                    </Chip>
                </Tooltip>;
            default:
                return <Tooltip content="Config could not be validated" placement="bottom" color="foreground">
                    <Chip size="sm" radius="sm" className="text-xs">
                        <Question/>
                    </Chip>
                </Tooltip>
        }
    }

    function trustLevelToBadge(trustLevel: PluginTrustLevel | undefined): React.ReactNode {
        switch (trustLevel) {
            case PluginTrustLevel.OFFICIAL:
                return <Tooltip color="foreground" placement="bottom" content="Official plugin">
                    <SealCheck className="fill-success"/>
                </Tooltip>;
            case PluginTrustLevel.BUNDLED:
                return <Tooltip color="foreground" placement="bottom" content="Bundled plugin">
                    <SealCheck/>
                </Tooltip>;
            case PluginTrustLevel.THIRD_PARTY:
                return <Tooltip color="foreground" placement="bottom" content="3rd party plugin">
                    <SealWarning/>
                </Tooltip>;
            case PluginTrustLevel.UNTRUSTED:
                return <Tooltip color="foreground" placement="bottom" content="Invalid plugin signature">
                    <SealWarning className="fill-danger"/>
                </Tooltip>;
            default:
                return <Tooltip color="foreground" placement="bottom" content="Unkown verification status">
                    <SealQuestion/>
                </Tooltip>;
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
            <Card
                className={`flex flex-row justify-between p-2 border-2 border-${borderColor(plugin.state, plugin.trustLevel)}`}>
                <div className="absolute right-0 top-0 flex flex-row">
                    <Tooltip content={`${isDisabled(plugin.state) ? "Enable" : "Disable"} plugin`} placement="bottom"
                             color="foreground">
                        <Button isIconOnly
                                variant="light"
                                onPress={() => togglePluginEnabled()}
                                isDisabled={plugin.state == PluginState.UNLOADED || plugin.state == PluginState.RESOLVED}
                        >
                            <Power/>
                        </Button>
                    </Tooltip>
                    <Tooltip content="Configuration" placement="bottom" color="foreground">
                        <Button isIconOnly variant="light" onPress={pluginDetailsModal.onOpen}>
                            <SlidersHorizontal/>
                        </Button>
                    </Tooltip>
                </div>
                <div className="flex flex-1 flex-col items-center gap-2">
                    <PluginLogo plugin={plugin}/>
                    <p className="flex flex-row items-center gap-1 font-semibold">
                        {plugin.name}
                        <IconContext.Provider value={{size: 18, weight: "fill"}}>
                            {trustLevelToBadge(plugin.trustLevel)}
                        </IconContext.Provider>
                    </p>
                    <div className="flex flex-row gap-2">
                        <Chip size="sm" radius="sm" className="text-xs">{plugin.version}</Chip>
                        <Chip size="sm" radius="sm" className="text-xs" color={stateToColor(plugin.state)}>
                            <Tooltip content={`Plugin ${plugin.state?.toLowerCase()}`} placement="bottom"
                                     color="foreground">
                                {stateToIcon(plugin.state)}
                            </Tooltip>
                        </Chip>
                        {configValidationResult === undefined ?
                            <Skeleton className="rounded-md h-6 w-9"/> :
                            configValidationResultToChip(configValidationResult)
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