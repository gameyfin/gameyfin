import {Subscription} from "@vaadin/hilla-frontend";
import PluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/PluginDto";
import PluginUpdateDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/PluginUpdateDto";
import {proxy} from "valtio/index";
import {PluginEndpoint} from "Frontend/generated/endpoints";

type PluginState = {
    subscription?: Subscription<PluginUpdateDto[]>;
    isLoaded: boolean;
    state: Record<string, PluginDto>;
    plugins: PluginDto[];
    pluginsByType: Record<string, PluginDto[]>;
};

export const pluginState = proxy<PluginState>({
    get isLoaded() {
        return this.subscription != null;
    },
    state: {},
    get plugins() {
        return Object.values<PluginDto>(this.state);
    },
    get pluginsByType() {
        return groupPluginsByType(this.state);
    }
});

/** Subscribe to and process state updates from backend **/
export async function initializePluginState() {
    if (pluginState.isLoaded) return;

    // Fetch initial plugin list
    const initialEntries = await PluginEndpoint.getAll();
    initialEntries.forEach((plugin: PluginDto) => {
        pluginState.state[plugin.id] = plugin;
    });

    // Subscribe to real-time updates
    pluginState.subscription = PluginEndpoint.subscribe().onNext((updateDtos: PluginUpdateDto[]) => {
        updateDtos.forEach((updateDto: PluginUpdateDto) => {
            // Make sure the plugin exists in the state
            if (pluginState.state[updateDto.id]) {
                // Update the existing plugin by merging the new data using destructuring
                pluginState.state[updateDto.id] = {
                    ...pluginState.state[updateDto.id],
                    ...updateDto
                };
            }
        })
    });
}

/** Computed **/

function groupPluginsByType(pluginsMap: Record<string, PluginDto>): Record<string, PluginDto[]> {
    const pluginsByType: Record<string, PluginDto[]> = {};

    // Convert map to array of plugins
    const plugins = Object.values(pluginsMap);

    // Iterate through each plugin
    for (const plugin of plugins) {
        // Each plugin can have multiple types
        for (const type of plugin.types) {
            // Initialize array for this type if it doesn't exist
            if (!pluginsByType[type]) {
                pluginsByType[type] = [];
            }

            // Add plugin to the appropriate type array
            pluginsByType[type].push(plugin);
        }
    }

    return pluginsByType;
}