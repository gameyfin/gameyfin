import {proxy} from 'valtio';
import ConfigEntryDto from "Frontend/generated/org/gameyfin/app/config/dto/ConfigEntryDto";
import {ConfigEndpoint} from "Frontend/generated/endpoints";
import ConfigUpdateDto from "Frontend/generated/org/gameyfin/app/config/dto/ConfigUpdateDto";
import {Subscription} from "@vaadin/hilla-frontend";

type ConfigState = {
    subscription?: Subscription<ConfigUpdateDto[]>;
    isLoaded: boolean;
    state: Record<string, ConfigEntryDto>;
    config: NestedConfig;
};

export const configState = proxy<ConfigState>({
    get isLoaded() {
        return this.subscription != null;
    },
    state: {},
    get config() {
        return toNestedConfig(Object.values(this.state));
    }
});

/** Subscribe to and process state updates from backend **/
let generation = 0;
export function resetConfigState() {
    generation++;
    configState.subscription?.cancel();
    configState.subscription = undefined;
    configState.state = {};
}

export async function initializeConfigState() {
    if (configState.isLoaded) return;
    const current = ++generation;

    // Fetch initial configuration data
    const initialEntries = await ConfigEndpoint.getAll();
    if (current !== generation) return;
    initialEntries.forEach((entry) => {
        configState.state[entry.key] = entry;
    });

    // Subscribe to real-time updates
    configState.subscription = ConfigEndpoint.subscribe().onNext((updateDtos: ConfigUpdateDto[]) => {
        if (current !== generation) return;
        updateDtos.forEach((updateDto: ConfigUpdateDto) => {
            Object.entries(updateDto.updates).forEach(([key, value]) => {
                if (configState.state[key]) {
                    configState.state[key].value = value;
                }
            });
        })
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
