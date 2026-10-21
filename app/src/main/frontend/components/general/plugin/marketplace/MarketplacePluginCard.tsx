import React from "react";
import {Button, Card, Chip, Spinner, Tooltip, useOverlayState} from "@heroui/react";
import {ArrowCircleUpIcon, CheckIcon, DownloadSimpleIcon, StorefrontIcon, WarningIcon} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import MarketplacePluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/marketplace/dto/MarketplacePluginDto";
import MarketplacePluginStatus
    from "Frontend/generated/org/gameyfin/app/core/plugins/marketplace/dto/MarketplacePluginStatus";
import {marketplaceState} from "Frontend/state/MarketplaceState";
import MarketplacePluginLogo from "Frontend/components/general/plugin/marketplace/MarketplacePluginLogo";
import MarketplacePluginStatusChip from "Frontend/components/general/plugin/marketplace/MarketplacePluginStatusChip";
import MarketplacePluginDetailsModal
    from "Frontend/components/general/plugin/marketplace/MarketplacePluginDetailsModal";
import {useMarketplaceInstall} from "Frontend/components/general/plugin/marketplace/useMarketplaceInstall";
import {camelCaseToTitle} from "Frontend/util/utils";

export default function MarketplacePluginCard({plugin}: { plugin: MarketplacePluginDto }) {
    const detailsModal = useOverlayState();
    const {install, isInstalling, confirmationModal} = useMarketplaceInstall(plugin);
    const isPending = (useSnapshot(marketplaceState).pending[plugin.id] ?? false) || isInstalling;

    return (
        <>
            <Card className="flex flex-col gap-3 p-4 h-full">
                <button type="button"
                        className="flex flex-col items-center gap-2 grow text-left cursor-pointer"
                        aria-label={`Show details for ${plugin.name}`}
                        onClick={detailsModal.open}>
                    <div className="flex h-16 w-full items-center justify-center">
                        <MarketplacePluginLogo plugin={plugin} glow={true}/>
                    </div>
                    <p className="flex flex-row items-center gap-1 font-semibold text-center">
                        {plugin.name}
                        <MarketplaceSourceBadge plugin={plugin}/>
                    </p>
                    {plugin.author && <p className="text-xs text-muted">by {plugin.author}</p>}
                    <p className="text-sm text-center line-clamp-3 w-full">
                        {plugin.shortDescription ?? plugin.description}
                    </p>
                    <div className="flex flex-row flex-wrap justify-center gap-1">
                        {plugin.types.map(type =>
                            <Chip key={type} size="sm" variant="secondary">{camelCaseToTitle(type)}</Chip>
                        )}
                    </div>
                </button>

                <div className="flex flex-row items-center justify-between gap-2">
                    <div className="flex flex-col gap-1">
                        <MarketplacePluginStatusChip plugin={plugin}/>
                        {plugin.latestVersion &&
                            <span className="text-xs text-muted">Latest: v{plugin.latestVersion}</span>}
                    </div>
                    <MarketplacePluginAction plugin={plugin} isPending={isPending} onInstall={() => install()}/>
                </div>
            </Card>

            <MarketplacePluginDetailsModal plugin={plugin}
                                           isOpen={detailsModal.isOpen}
                                           onOpenChange={detailsModal.setOpen}
                                           onInstall={install}
                                           isPending={isPending}/>
            {confirmationModal}
        </>
    );
}

export function MarketplaceSourceBadge({plugin}: { plugin: MarketplacePluginDto }) {
    if (plugin.officialMarketplace) return null;

    return (
        <Tooltip delay={0}>
            <Tooltip.Trigger className="flex">
                <span className="inline-flex text-warning" aria-label="Third-party marketplace">
                    <StorefrontIcon size={18} weight="fill"/>
                </span>
            </Tooltip.Trigger>
            <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                {`Provided by third-party marketplace "${plugin.marketplaceName}"`}
            </Tooltip.Content>
        </Tooltip>
    );
}

interface MarketplacePluginActionProps {
    plugin: MarketplacePluginDto;
    isPending: boolean;
    onInstall: () => void;
}

function MarketplacePluginAction({plugin, isPending, onInstall}: MarketplacePluginActionProps) {
    switch (plugin.status) {
        case MarketplacePluginStatus.NOT_INSTALLED:
            return (
                <Button size="sm" variant="primary" onPress={onInstall} isPending={isPending}>
                    {isPending ? <Spinner size="sm" color="current"/> : <DownloadSimpleIcon/>}
                    Install
                </Button>
            );
        case MarketplacePluginStatus.UPDATE_AVAILABLE:
            return (
                <Button size="sm" variant="primary" onPress={onInstall} isPending={isPending}>
                    {isPending ? <Spinner size="sm" color="current"/> : <ArrowCircleUpIcon/>}
                    Update to v{plugin.latestCompatibleVersion}
                </Button>
            );
        case MarketplacePluginStatus.INSTALLED:
            return (
                <Button size="sm" variant="ghost" isDisabled={true}>
                    <CheckIcon/>
                    Installed
                </Button>
            );
        case MarketplacePluginStatus.INCOMPATIBLE:
            return (
                <Tooltip delay={0}>
                    <Tooltip.Trigger>
                        <Button size="sm" variant="ghost" isDisabled={true}>
                            <WarningIcon/>
                            Incompatible
                        </Button>
                    </Tooltip.Trigger>
                    <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                        {`No version of this plugin is compatible with your Gameyfin version` +
                            (plugin.releases[0]?.requires ? ` (latest requires ${plugin.releases[0].requires})` : "")}
                    </Tooltip.Content>
                </Tooltip>
            );
    }
}
