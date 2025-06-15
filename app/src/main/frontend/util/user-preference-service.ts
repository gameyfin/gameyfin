import {UserPreferencesEndpoint} from "Frontend/generated/endpoints";

export class UserPreferenceService {
    static LOCAL_STORAGE_PREFIX = "gameyfin.";

    static async sync(): Promise<void> {
        let keys = Object.keys(localStorage);
        for (let key of keys) {
            if (!key.startsWith(`${this.LOCAL_STORAGE_PREFIX}`)) {
                continue;
            }
            let value = await UserPreferencesEndpoint.get(key.replace(this.LOCAL_STORAGE_PREFIX, ""));
            if (value) {
                localStorage.setItem(key, value);
            }
        }
    }

    static async get(key: string): Promise<string | undefined> {
        let localPreference = localStorage.getItem(`${this.LOCAL_STORAGE_PREFIX}${key}`);

        if (localPreference) {
            return localPreference;
        } else {
            let syncedPreference = await UserPreferencesEndpoint.get(key);
            if (syncedPreference) {
                localStorage.setItem(`${this.LOCAL_STORAGE_PREFIX}${key}`, syncedPreference);
                return syncedPreference;
            }
        }

        return undefined;
    }

    static async set(key: string, value: string) {
        await UserPreferencesEndpoint.set(key, value);
        localStorage.setItem(`${this.LOCAL_STORAGE_PREFIX}${key}`, value);
    }
}