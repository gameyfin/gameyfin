import {proxy} from 'valtio';
import ConfigEntryDto from "Frontend/generated/de/grimsi/gameyfin/config/dto/ConfigEntryDto";
import {ConfigEndpoint} from "Frontend/generated/endpoints";
import ConfigUpdateDto from "Frontend/generated/de/grimsi/gameyfin/config/dto/ConfigUpdateDto";
import {Subscription} from "@vaadin/hilla-frontend";

type ConfigState = {
    subscription?: Subscription<ConfigUpdateDto>;
    isLoaded: boolean;
    configEntries: Record<string, ConfigEntryDto>;
    configNested: NestedConfig;
};

export const configState = proxy<ConfigState>({
    get isLoaded() {
        return this.subscription != null;
    },
    configEntries: {},
    get configNested() {
        return toNestedConfig(Object.values(this.configEntries));
    }
});

/** Subscribe to and process state updates from backend **/
export async function initializeConfig() {
    if (configState.isLoaded) return;

    // Fetch initial configuration data
    const initialEntries = await ConfigEndpoint.getAll();
    initialEntries.forEach((entry) => {
        configState.configEntries[entry.key] = entry;
    });

    // Subscribe to real-time updates
    configState.subscription = ConfigEndpoint.subscribe().onNext((updateDto: ConfigUpdateDto) => {
        Object.entries(updateDto.updates).forEach(([key, value]) => {
            if (configState.configEntries[key]) {
                configState.configEntries[key].value = value;
            }
        });
    });
}

/** Computed properties **/

export type NestedConfig = {
    [field: string]: any;
}

function toNestedConfig(entries: ConfigEntryDto[]): NestedConfig {
    const result: Record<string, any> = {};

    for (const entry of entries) {
        const keys = entry.key.split('.');
        let current = result;

        for (let i = 0; i < keys.length; i++) {
            const key = keys[i];

            if (i === keys.length - 1) {
                current[key] = entry.value;
            } else {
                current[key] = current[key] || {};
                current = current[key];
            }
        }
    }

    return result;
}