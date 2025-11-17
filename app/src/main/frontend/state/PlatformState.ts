import {proxy} from 'valtio';
import {PlatformEndpoint} from "Frontend/generated/endpoints";
import PlatformStatsDto from "Frontend/generated/org/gameyfin/app/platforms/dto/PlatformStatsDto";
import {Subscription} from "@vaadin/hilla-frontend";

type PlatformState = {
    subscription?: Subscription<PlatformStatsDto[]>;
    isLoaded: boolean;
    available: Set<string>;
    usedByGames: Set<string>;
    usedByLibraries: Set<string>;
};

export const platformState = proxy<PlatformState>({
    get isLoaded() {
        return this.subscription != null;
    },
    available: new Set<string>,
    usedByGames: new Set<string>,
    usedByLibraries: new Set<string>
});

/** Subscribe to and process platform updates from backend **/
export async function initializePlatformState() {
    if (platformState.isLoaded) return;

    // Fetch initial configuration data
    const initialPlatformStats = await PlatformEndpoint.getStats();
    platformState.available = new Set(initialPlatformStats.available);
    platformState.usedByGames = new Set(initialPlatformStats.inUseByGames);
    platformState.usedByLibraries = new Set(initialPlatformStats.inUseByLibraries);

    // Subscribe to real-time updates
    platformState.subscription = PlatformEndpoint.subscribe().onNext((platformStats: Partial<PlatformStatsDto>[]) => {
        platformStats.forEach((updateDto: Partial<PlatformStatsDto>) => {
            if (updateDto.available !== undefined) {
                platformState.available = new Set(updateDto.available);
            }
            if (updateDto.inUseByGames !== undefined) {
                platformState.usedByGames = new Set(updateDto.inUseByGames);
            }
            if (updateDto.inUseByLibraries !== undefined) {
                platformState.usedByLibraries = new Set(updateDto.inUseByLibraries);
            }
        })
    });
}