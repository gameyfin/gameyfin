import Popup from "Frontend/components/general/Popup";
import {Button, Card, Chip, Spinner, toast, Tooltip, useOverlayState} from "@heroui/react";
import {
    ArrowCircleUpIcon,
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
    StorefrontIcon,
    TrashIcon,
    WarningCircleIcon,
    WarningIcon,
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
import PluginOrigin from "Frontend/generated/org/gameyfin/app/core/plugins/management/PluginOrigin";
import {useSnapshot} from "valtio/react";
import {installPlugin, marketplaceState, uninstallPlugin} from "Frontend/state/MarketplaceState";
import ConfirmModal from "Frontend/components/general/modals/ConfirmModal";

const BORDER_CLASSES = {
    success: "border-success",
    warning: "border-warning",
    danger: "border-danger",
    default: "border-default"
};

const CHIP_CLASSES = {
    success: "bg-success text-foreground",
    warning: "bg-warning text-foreground",
    danger: "bg-danger text-foreground",
    default: "bg-default text-foreground"
};

export function PluginManagementCard({plugin}: { plugin: PluginDto }) {
    const pluginDetailsModal = useOverlayState();
    const uninstallModal = useOverlayState();
    const marketplace = useSnapshot(marketplaceState);

    const availableUpdate = marketplace.updates[plugin.id];
    const isPending = marketplace.pending[plugin.id] ?? false;
    const isMarketplacePlugin = plugin.origin === PluginOrigin.MARKETPLACE;
    const sourceMarketplace = marketplace.marketplaces.find(m => m.id === plugin.sourceMarketplaceId);

    async function update() {
        if (!availableUpdate) return;
        try {
            await installPlugin(availableUpdate.marketplaceId, plugin.id, availableUpdate.latestCompatibleVersion);
            toast.info("Plugin update started", {
                description: "Progress is shown in the active tasks popover."
            });
        } catch (e: any) {
            toast.danger("Update failed", {description: e?.message ?? `Failed to update ${plugin.name}`});
        }
    }

    async function uninstall() {
        try {
            await uninstallPlugin(plugin.id);
            uninstallModal.close();
            toast.info("Plugin uninstallation started", {
                description: "Progress is shown in the active tasks popover."
            });
        } catch (e: any) {
            toast.danger("Uninstall failed", {description: e?.message ?? `Failed to uninstall ${plugin.name}`});
        }
    }

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
                return <Chip size="sm" className={`text-xs rounded-xl h-6 ${CHIP_CLASSES.success}`}>
                    <Popup>
                        <Popup.Trigger className="flex">
                            <span className="inline-flex">
                                <CheckCircleIcon/>
                            </span>
                        </Popup.Trigger>
                        <Popup.Content placement="bottom" className="bg-foreground text-background">
                            Config valid
                        </Popup.Content>
                    </Popup>
                </Chip>
            case PluginConfigValidationResultType.INVALID:
                return <Chip size="sm" className={`text-xs rounded-xl h-6 ${CHIP_CLASSES.danger}`}>
                    <Popup>
                        <Popup.Trigger className="flex">
                            <span className="inline-flex">
                                <WarningCircleIcon/>
                            </span>
                        </Popup.Trigger>
                        <Popup.Content placement="bottom" className="bg-foreground text-background">
                            Config invalid
                        </Popup.Content>
                    </Popup>
                </Chip>;
            default:
                return <Chip size="sm" className="text-xs rounded-xl h-6">
                    <Popup>
                        <Popup.Trigger className="flex">
                            <span className="inline-flex">
                                <QuestionIcon/>
                            </span>
                        </Popup.Trigger>
                        <Popup.Content placement="bottom" className="bg-foreground text-background">
                            Config could not be validated
                        </Popup.Content>
                    </Popup>
                </Chip>
        }
    }

    function trustLevelToBadge(trustLevel: PluginTrustLevel | undefined): React.ReactNode {
        switch (trustLevel) {
            case PluginTrustLevel.OFFICIAL:
                return <Popup>
                    <Popup.Trigger className="flex">
                        <span className="inline-flex">
                            <SealCheckIcon className="fill-success"/>
                        </span>
                    </Popup.Trigger>
                    <Popup.Content placement="bottom" className="bg-foreground text-background">
                        Official plugin
                    </Popup.Content>
                </Popup>;
            case PluginTrustLevel.BUNDLED:
                return <Popup>
                    <Popup.Trigger className="flex">
                        <span className="inline-flex">
                            <SealCheckIcon/>
                        </span>
                    </Popup.Trigger>
                    <Popup.Content placement="bottom" className="bg-foreground text-background">
                        Bundled plugin
                    </Popup.Content>
                </Popup>;
            case PluginTrustLevel.THIRD_PARTY:
                return <Popup>
                    <Popup.Trigger className="flex">
                        <span className="inline-flex">
                            <SealWarningIcon/>
                        </span>
                    </Popup.Trigger>
                    <Popup.Content placement="bottom" className="bg-foreground text-background">
                        3rd party plugin
                    </Popup.Content>
                </Popup>;
            case PluginTrustLevel.UNTRUSTED:
                return <Popup>
                    <Popup.Trigger className="flex">
                        <span className="inline-flex">
                            <SealWarningIcon className="fill-danger"/>
                        </span>
                    </Popup.Trigger>
                    <Popup.Content placement="bottom" className="bg-foreground text-background">
                        Invalid plugin signature
                    </Popup.Content>
                </Popup>;
            default:
                return <Popup>
                    <Popup.Trigger className="flex">
                        <span className="inline-flex">
                            <SealQuestionIcon/>
                        </span>
                    </Popup.Trigger>
                    <Popup.Content placement="bottom" className="bg-foreground text-background">
                        Unknown verification status
                    </Popup.Content>
                </Popup>;
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
                className={`flex flex-row justify-between p-2 border-2 ${BORDER_CLASSES[borderColor(plugin.state, plugin.trustLevel)]}`}>
                <div className="absolute left-0 top-0 flex flex-row">
                    {availableUpdate &&
                        <Tooltip delay={0}>
                            <Tooltip.Trigger>
                                <Button isIconOnly
                                        variant="ghost"
                                        aria-label="Update plugin"
                                        onPress={update}
                                        isDisabled={isPending}>
                                    {isPending ? <Spinner size="sm" color="current"/> :
                                        <ArrowCircleUpIcon className="fill-accent" weight="fill"/>}
                                </Button>
                            </Tooltip.Trigger>
                            <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                                {`Update to version ${availableUpdate.latestCompatibleVersion}`}
                            </Tooltip.Content>
                        </Tooltip>
                    }
                    {isMarketplacePlugin &&
                        <Tooltip delay={0}>
                            <Tooltip.Trigger>
                                <Button isIconOnly
                                        variant="ghost"
                                        aria-label="Uninstall plugin"
                                        onPress={uninstallModal.open}
                                        isDisabled={isPending}>
                                    <TrashIcon/>
                                </Button>
                            </Tooltip.Trigger>
                            <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                                Uninstall plugin
                            </Tooltip.Content>
                        </Tooltip>
                    }
                </div>
                <div className="absolute right-0 top-0 flex flex-row">
                    <Tooltip delay={0}>
                        <Tooltip.Trigger>
                            <Button isIconOnly
                                    variant="ghost"
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
                            <Button isIconOnly variant="ghost" onPress={pluginDetailsModal.open}>
                                <SlidersHorizontalIcon/>
                            </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                            Configuration
                        </Tooltip.Content>
                    </Tooltip>
                </div>
                <div className="flex min-w-0 flex-1 flex-col items-center gap-2">
                    <div className="flex h-16 w-full items-center justify-center">
                        <PluginLogo plugin={plugin} glow={true}/>
                    </div>
                    <p className="flex flex-wrap justify-center wrap-anywhere items-center gap-1 font-semibold">
                        {plugin.name}
                        <IconContext.Provider value={{size: 18, weight: "fill"}}>
                            {trustLevelToBadge(plugin.trustLevel)}
                            {isMarketplacePlugin &&
                                <Popup>
                                    <Popup.Trigger className="flex">
                                        <span className="inline-flex">
                                            <StorefrontIcon/>
                                        </span>
                                    </Popup.Trigger>
                                    <Popup.Content placement="bottom" className="bg-foreground text-background">
                                        {sourceMarketplace
                                            ? `Installed from marketplace "${sourceMarketplace.name}"`
                                            : "Installed from a marketplace"}
                                    </Popup.Content>
                                </Popup>
                            }
                            {!plugin.compatible &&
                                <Popup>
                                    <Popup.Trigger className="flex">
                                        <span className="inline-flex">
                                            <WarningIcon className="fill-danger"/>
                                        </span>
                                    </Popup.Trigger>
                                    <Popup.Content placement="bottom" className="bg-foreground text-background">
                                        {`Incompatible with this Gameyfin version (requires ${plugin.requires})`}
                                    </Popup.Content>
                                </Popup>
                            }
                        </IconContext.Provider>
                    </p>
                    <div className="flex flex-wrap justify-center items-center gap-2 mt-auto">
                        <Chip size="sm" className="text-xs rounded-xl h-6">{plugin.version}</Chip>
                        <Chip size="sm"
                              className={`text-xs rounded-xl h-6 ${CHIP_CLASSES[stateToColor(plugin.state)]}`}>
                            <Popup>
                                <Popup.Trigger className="flex">
                                   <span className="inline-flex">
                                       {stateToIcon(plugin.state)}
                                   </span>
                                </Popup.Trigger>
                                <Popup.Content placement="bottom" className="bg-foreground text-background">
                                    {`Plugin ${plugin.state?.toLowerCase()}`}
                                </Popup.Content>
                            </Popup>
                        </Chip>
                        {configValidationResultToChip(plugin.configValidation)}
                    </div>
                </div>
            </Card>
            <PluginDetailsModal plugin={plugin}
                                isOpen={pluginDetailsModal.isOpen}
                                onOpenChange={pluginDetailsModal.setOpen}
            />
            <ConfirmModal isOpen={uninstallModal.isOpen}
                          onOpenChange={uninstallModal.setOpen}
                          title={`Uninstall ${plugin.name}`}
                          confirmLabel="Uninstall"
                          isPending={isPending}
                          onConfirm={uninstall}>
                <p>Do you really want to uninstall <b>{plugin.name}</b> (version {plugin.version})?</p>
                <p className="text-muted">
                    If a bundled version of this plugin exists, it will be used instead.
                    The configuration of the plugin is kept.
                </p>
            </ConfirmModal>
        </>

    )
}
