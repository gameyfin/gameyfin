import {UserPreferencesEndpoint} from "Frontend/generated/endpoints";
import {useAuth} from "Frontend/util/auth";

export function useUserPreferenceService() {
    const LOCAL_STORAGE_PREFIX = "gameyfin.";
    const auth = useAuth();

    async function sync(): Promise<void> {
        if (auth.state.user === undefined) return;

        let keys = Object.keys(localStorage);
        for (let key of keys) {
            if (!key.startsWith(LOCAL_STORAGE_PREFIX)) {
                continue;
            }
            let value = await UserPreferencesEndpoint.get(key.replace(LOCAL_STORAGE_PREFIX, ""));
            if (value) {
                localStorage.setItem(key, value);
            }
        }
    }

    async function get(key: string): Promise<string | undefined> {
        let localPreference = localStorage.getItem(`${LOCAL_STORAGE_PREFIX}${key}`);
        if (localPreference) {
            return localPreference;
        } else {
            if (auth.state.user === undefined) return undefined;
            let syncedPreference = await UserPreferencesEndpoint.get(key);
            if (syncedPreference) {
                localStorage.setItem(`${LOCAL_STORAGE_PREFIX}${key}`, syncedPreference);
                return syncedPreference;
            }
        }
        return undefined;
    }

    async function set(key: string, value: string): Promise<void> {
        localStorage.setItem(`${LOCAL_STORAGE_PREFIX}${key}`, value);
        if (auth.state.user === undefined) return;
        await UserPreferencesEndpoint.set(key, value);
    }

    return {sync, get, set};
}