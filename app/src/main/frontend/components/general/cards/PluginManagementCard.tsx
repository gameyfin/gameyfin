import {Button, Card, Chip, Tooltip, useOverlayState} from "@heroui/react";
import {
    CheckCircleIcon,
    IconContext,
    PauseCircleIcon,
    PlayCircleIcon,
    PowerIcon,
    QuestionIcon,
    QuestionMarkIcon,
    SealCheckIcon,
    SealQuestionIcon,
    SealWarningIcon,
    SlidersHorizontalIcon,
    StopCircleIcon,
    WarningCircleIcon,
    XCircleIcon
} from "@phosphor-icons/react";
import PluginState from "Frontend/generated/org/pf4j/PluginState";
import React, {ReactNode} from "react";
import PluginDetailsModal from "Frontend/components/general/modals/PluginDetailsModal";
import PluginLogo from "Frontend/components/general/plugin/PluginLogo";
import PluginTrustLevel from "Frontend/generated/org/gameyfin/app/core/plugins/management/PluginTrustLevel";
import {PluginEndpoint} from "Frontend/generated/endpoints";
import PluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/PluginDto";
import PluginConfigValidationResult
    from "Frontend/generated/org/gameyfin/pluginapi/core/config/PluginConfigValidationResult";
import PluginConfigValidationResultType
    from "Frontend/generated/org/gameyfin/pluginapi/core/config/PluginConfigValidationResultType";

export function PluginManagementCard({plugin}: { plugin: PluginDto }) {
    const pluginDetailsModal = useOverlayState();

    function borderColor(state: PluginState | undefined, trustLevel: PluginTrustLevel | undefined): "success" | "warning" | "danger" | "default" {
        if (trustLevel === PluginTrustLevel.UNTRUSTED) return "danger";

        if (isDisabled(state)) return "warning";
        return stateToColor(state);
    }

    function stateToColor(state: PluginState | undefined): "success" | "warning" | "danger" | "default" {
        switch (state) {
            case PluginState.STARTED:
                return "success";
            case PluginState.DISABLED:
                return "warning";
            case PluginState.FAILED:
            case PluginState.STOPPED:
                return "danger";
            default:
                return "default";
        }
    }

    function stateToIcon(state: PluginState | undefined): ReactNode {
        switch (state) {
            case PluginState.STARTED:
                return <PlayCircleIcon/>;
            case PluginState.DISABLED:
                return <PauseCircleIcon/>;
            case PluginState.STOPPED:
            case PluginState.FAILED:
                return <StopCircleIcon/>;
            case PluginState.UNLOADED:
            case PluginState.RESOLVED:
                return <XCircleIcon/>;
            default:
                return <QuestionMarkIcon/>;
        }
    }

    function configValidationResultToChip(validationResult: PluginConfigValidationResult | undefined): ReactNode {
        switch (validationResult?.result) {
            case PluginConfigValidationResultType.VALID:
                return <Tooltip delay={0}>
                    <Tooltip.Trigger>
                        <Chip size="sm" className="text-xs rounded-sm" color="success">
                            <CheckCircleIcon/>
                        </Chip>
                    </Tooltip.Trigger>
                    <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                        Config valid
                    </Tooltip.Content>
                </Tooltip>
            case PluginConfigValidationResultType.INVALID:
                return <Tooltip delay={0}>
                    <Tooltip.Trigger>
                        <Chip size="sm" className="text-xs rounded-sm" color="danger">
                            <WarningCircleIcon/>
                        </Chip>
                    </Tooltip.Trigger>
                    <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                        Config invalid
                    </Tooltip.Content>
                </Tooltip>;
            default:
                return <Tooltip delay={0}>
                    <Tooltip.Trigger>
                        <Chip size="sm" className="text-xs rounded-sm">
                            <QuestionIcon/>
                        </Chip>
                    </Tooltip.Trigger>
                    <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                        Config could not be validated
                    </Tooltip.Content>
                </Tooltip>
        }
    }

    function trustLevelToBadge(trustLevel: PluginTrustLevel | undefined): React.ReactNode {
        switch (trustLevel) {
            case PluginTrustLevel.OFFICIAL:
                return <Tooltip delay={0}>
                    <Tooltip.Trigger>
                        <span className="inline-flex">
                            <SealCheckIcon className="fill-success"/>
                        </span>
                    </Tooltip.Trigger>
                    <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                        Official plugin
                    </Tooltip.Content>
                </Tooltip>;
            case PluginTrustLevel.BUNDLED:
                return <Tooltip delay={0}>
                    <Tooltip.Trigger>
                        <span className="inline-flex">
                            <SealCheckIcon/>
                        </span>
                    </Tooltip.Trigger>
                    <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                        Bundled plugin
                    </Tooltip.Content>
                </Tooltip>;
            case PluginTrustLevel.THIRD_PARTY:
                return <Tooltip delay={0}>
                    <Tooltip.Trigger>
                        <span className="inline-flex">
                            <SealWarningIcon/>
                        </span>
                    </Tooltip.Trigger>
                    <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                        3rd party plugin
                    </Tooltip.Content>
                </Tooltip>;
            case PluginTrustLevel.UNTRUSTED:
                return <Tooltip delay={0}>
                    <Tooltip.Trigger>
                        <span className="inline-flex">
                            <SealWarningIcon className="fill-danger"/>
                        </span>
                    </Tooltip.Trigger>
                    <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                        Invalid plugin signature
                    </Tooltip.Content>
                </Tooltip>;
            default:
                return <Tooltip delay={0}>
                    <Tooltip.Trigger>
                        <span className="inline-flex">
                            <SealQuestionIcon/>
                        </span>
                    </Tooltip.Trigger>
                    <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                        Unkown verification status
                    </Tooltip.Content>
                </Tooltip>;
        }
    }

    function isDisabled(state: PluginState | undefined): boolean {
        return state === PluginState.DISABLED;
    }

    async function togglePluginEnabled() {
        if (isDisabled(plugin.state)) {
            await PluginEndpoint.enablePlugin(plugin.id);
        } else {
            await PluginEndpoint.disablePlugin(plugin.id);
        }
    }

    // @ts-ignore
    return (
        <>
            <Card
                className={`flex flex-row justify-between p-2 border-2 border-${borderColor(plugin.state, plugin.trustLevel)}`}>
                <div className="absolute right-0 top-0 flex flex-row">
                    <Tooltip delay={0}>
                        <Tooltip.Trigger>
                            <Button isIconOnly
                                    variant="tertiary"
                                    onPress={() => togglePluginEnabled()}
                                    isDisabled={plugin.state == PluginState.UNLOADED || plugin.state == PluginState.RESOLVED}
                            >
                                <PowerIcon/>
                            </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                            {`${isDisabled(plugin.state) ? "Enable" : "Disable"} plugin`}
                        </Tooltip.Content>
                    </Tooltip>
                    <Tooltip delay={0}>
                        <Tooltip.Trigger>
                            <Button isIconOnly variant="tertiary" onPress={pluginDetailsModal.open}>
                                <SlidersHorizontalIcon/>
                            </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                            Configuration
                        </Tooltip.Content>
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
                        <Chip size="sm" className="text-xs rounded-sm">{plugin.version}</Chip>
                        <Chip size="sm" className="text-xs rounded-sm" color={stateToColor(plugin.state)}>
                            <Tooltip delay={0}>
                                <Tooltip.Trigger>
                                   <span className="inline-flex">
                                       {stateToIcon(plugin.state)}
                                   </span>
                                </Tooltip.Trigger>
                                <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                                   {`Plugin ${plugin.state?.toLowerCase()}`}
                                </Tooltip.Content>
                            </Tooltip>
                        </Chip>
                        {configValidationResultToChip(plugin.configValidation)}
                    </div>
                </div>
            </Card>
            <PluginDetailsModal plugin={plugin}
                                isOpen={pluginDetailsModal.isOpen}
                                onOpenChange={pluginDetailsModal.setOpen}
            />
        </>

    )
}