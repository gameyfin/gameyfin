import {proxy} from 'valtio';
import ConfigEntryDto from "Frontend/generated/de/grimsi/gameyfin/config/dto/ConfigEntryDto";
import {ConfigEndpoint} from "Frontend/generated/endpoints";
import ConfigUpdateDto from "Frontend/generated/de/grimsi/gameyfin/config/dto/ConfigUpdateDto";

type ConfigState = {
    isLoaded: boolean;
    configEntries: Record<string, ConfigEntryDto>;
    configNested: NestedConfig;
};

export const configState = proxy<ConfigState>({
    isLoaded: false,
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
    configState.isLoaded = true;

    // Subscribe to real-time updates
    ConfigEndpoint.subscribe().onNext((updateDto: ConfigUpdateDto) => {
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

function toNestedConfig(configArray: ConfigEntryDto[]): NestedConfig {
    const nestedConfig: NestedConfig = {};

    configArray.forEach(item => {
        const keys = item.key!.split('.');
        let currentLevel = nestedConfig;

        // Traverse the nested structure and create objects as needed
        keys.forEach((key, index) => {
            if (index === keys.length - 1) {
                // Convert value to the appropriate type
                let value: any;
                switch (item.type) {
                    case 'Boolean':
                        value = typeof item.value == 'boolean' ? item.value : item.value === 'true';
                        break;
                    case 'Int':
                        value = typeof item.value == 'number' ? item.value : 0;
                        break;
                    case 'Float':
                        value = typeof item.value == 'number' ? item.value : 0.0;
                        break;
                    case 'Array':
                        if (Array.isArray(item.value)) {
                            switch (item.elementType) {
                                case 'Boolean':
                                    value = item.value.map(v => typeof v === 'boolean' ? v : v === 'true');
                                    break;
                                case 'Int':
                                case 'Integer':
                                    value = item.value.map(v => typeof v == 'number' ? v : 0);
                                    break;
                                case 'Float':
                                    value = item.value.map(v => typeof v == 'number' ? v : 0.0);
                                    break;
                                case 'String':
                                default:
                                    value = item.value.map(v => v.toString());
                                    break;
                            }
                        } else {
                            value = [];
                        }
                        break;
                    case 'String':
                    default:
                        value = item.value;
                        break;
                }
                currentLevel[key] = value;
            } else {
                if (!currentLevel[key]) {
                    currentLevel[key] = {};
                }
                currentLevel = currentLevel[key];
            }
        });
    });
    return nestedConfig;
}