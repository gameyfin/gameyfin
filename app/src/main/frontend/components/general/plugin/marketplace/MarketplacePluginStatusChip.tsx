import React from "react";
import {Chip} from "@heroui/react";
import MarketplacePluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/marketplace/dto/MarketplacePluginDto";
import MarketplacePluginStatus
    from "Frontend/generated/org/gameyfin/app/core/plugins/marketplace/dto/MarketplacePluginStatus";

export const statusLabels: Record<MarketplacePluginStatus, string> = {
    [MarketplacePluginStatus.NOT_INSTALLED]: "Not installed",
    [MarketplacePluginStatus.INSTALLED]: "Installed",
    [MarketplacePluginStatus.UPDATE_AVAILABLE]: "Update available",
    [MarketplacePluginStatus.INCOMPATIBLE]: "Incompatible"
};

const statusColors: Record<MarketplacePluginStatus, "default" | "accent" | "success" | "danger" | "warning"> = {
    [MarketplacePluginStatus.NOT_INSTALLED]: "default",
    [MarketplacePluginStatus.INSTALLED]: "success",
    [MarketplacePluginStatus.UPDATE_AVAILABLE]: "accent",
    [MarketplacePluginStatus.INCOMPATIBLE]: "danger"
};

export default function MarketplacePluginStatusChip({plugin}: { plugin: MarketplacePluginDto }) {
    const label = plugin.status === MarketplacePluginStatus.INSTALLED || plugin.status === MarketplacePluginStatus.UPDATE_AVAILABLE
        ? `${statusLabels[plugin.status]} (v${plugin.installedVersion})`
        : statusLabels[plugin.status];

    return <Chip size="sm" variant="soft" color={statusColors[plugin.status]}>{label}</Chip>;
}
