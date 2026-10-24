import {proxy, subscribe} from "valtio/index";
import {toast} from "@heroui/react";
import {PluginMarketplaceEndpoint} from "Frontend/generated/endpoints";
import MarketplacePluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/marketplace/dto/MarketplacePluginDto";
import PluginMarketplaceDto from "Frontend/generated/org/gameyfin/app/core/plugins/marketplace/dto/PluginMarketplaceDto";
import MarketplacePluginStatus
    from "Frontend/generated/org/gameyfin/app/core/plugins/marketplace/dto/MarketplacePluginStatus";
import PluginMarketplaceCreateDto
    from "Frontend/generated/org/gameyfin/app/core/plugins/marketplace/dto/PluginMarketplaceCreateDto";
import PluginMarketplaceUpdateDto
    from "Frontend/generated/org/gameyfin/app/core/plugins/marketplace/dto/PluginMarketplaceUpdateDto";
import {isRunActive, taskState, trackRun} from "Frontend/state/TaskState";
import type TaskRunDto from "Frontend/generated/org/gameyfin/app/core/tasks/dto/TaskRunDto";

type MarketplaceState = {
    isLoaded: boolean;
    isLoading: boolean;
    plugins: MarketplacePluginDto[];
    marketplaces: PluginMarketplaceDto[];
    /** Plugin IDs which are currently being installed/updated/uninstalled */
    pending: Record<string, boolean>;
    /** Marketplace entries with an available update, keyed by plugin ID */
    updates: Record<string, MarketplacePluginDto>;
};

export const marketplaceState = proxy<MarketplaceState>({
    isLoaded: false,
    isLoading: false,
    plugins: [],
    marketplaces: [],
    pending: {},
    get updates() {
        const updates: Record<string, MarketplacePluginDto> = {};
        for (const plugin of this.plugins as MarketplacePluginDto[]) {
            if (plugin.status !== MarketplacePluginStatus.UPDATE_AVAILABLE) continue;

            // Prefer the marketplace the plugin was installed from
            const existing = updates[plugin.id];
            if (!existing || plugin.marketplaceId === plugin.installedFromMarketplaceId) {
                updates[plugin.id] = plugin;
            }
        }
        return updates;
    }
});

/** Loads the marketplace data (only once unless forced) **/
export async function initializeMarketplaceState(force = false) {
    if (marketplaceState.isLoading || (marketplaceState.isLoaded && !force)) return;

    marketplaceState.isLoading = true;
    try {
        const [plugins, marketplaces] = await Promise.all([
            force ? PluginMarketplaceEndpoint.refresh() : PluginMarketplaceEndpoint.getPlugins(),
            PluginMarketplaceEndpoint.getMarketplaces()
        ]);
        marketplaceState.plugins = plugins;
        marketplaceState.marketplaces = marketplaces;
        marketplaceState.isLoaded = true;
    } finally {
        marketplaceState.isLoading = false;
    }
}

async function reloadPlugins() {
    marketplaceState.plugins = await PluginMarketplaceEndpoint.getPlugins();
}

async function reloadMarketplaces() {
    marketplaceState.marketplaces = await PluginMarketplaceEndpoint.getMarketplaces();
}

async function withPending(pluginId: string, action: () => Promise<TaskRunDto>): Promise<TaskRunDto> {
    marketplaceState.pending[pluginId] = true;
    try {
        const run = await action();
        trackRun(run);
        // Keep the controls pending after the request returns, until the background work finishes.
        const unsubscribe = subscribe(taskState.runs, checkFinished);
        function checkFinished() {
            const current = taskState.runs[run.id];
            if (!current || isRunActive(current)) return;
            unsubscribe();
            void reloadPlugins().catch((e: any) => {
                toast.danger("Could not refresh marketplace", {description: e?.message});
            }).finally(() => {
                delete marketplaceState.pending[pluginId];
            });
        }
        checkFinished();
        return run;
    } catch (e) {
        delete marketplaceState.pending[pluginId];
        throw e;
    }
}

export async function installPlugin(marketplaceId: string, pluginId: string, version?: string) {
    return withPending(pluginId, () => PluginMarketplaceEndpoint.install(marketplaceId, pluginId, version));
}

export async function updatePlugin(pluginId: string, version?: string) {
    return withPending(pluginId, () => PluginMarketplaceEndpoint.update(pluginId, version));
}

export async function uninstallPlugin(pluginId: string) {
    return withPending(pluginId, () => PluginMarketplaceEndpoint.uninstall(pluginId));
}

export async function addMarketplace(marketplace: PluginMarketplaceCreateDto) {
    const result = await PluginMarketplaceEndpoint.addMarketplace(marketplace);
    await Promise.all([reloadMarketplaces(), reloadPlugins()]);
    return result;
}

export async function updateMarketplace(id: string, update: PluginMarketplaceUpdateDto) {
    const result = await PluginMarketplaceEndpoint.updateMarketplace(id, update);
    await Promise.all([reloadMarketplaces(), reloadPlugins()]);
    return result;
}

export async function removeMarketplace(id: string) {
    await PluginMarketplaceEndpoint.removeMarketplace(id);
    await Promise.all([reloadMarketplaces(), reloadPlugins()]);
}
