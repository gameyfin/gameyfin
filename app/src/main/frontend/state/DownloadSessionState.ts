import {proxy} from 'valtio';
import {BandwidthMonitoringEndpoint} from "Frontend/generated/endpoints";
import SessionStatsDto from "Frontend/generated/org/gameyfin/app/core/download/bandwidth/SessionStatsDto";
import {Subscription} from "@vaadin/hilla-frontend";

type DownloadSessionState = {
    subscription?: Subscription<SessionStatsDto[][]>;
    isLoaded: boolean;
    all: SessionStatsDto[];
    active: SessionStatsDto[];
};

export const downloadSessionState = proxy<DownloadSessionState>({
    get isLoaded() {
        return this.subscription != null;
    },
    all: [],
    get active() {
        const activeSessions = this.all.filter((session: SessionStatsDto) => session.activeDownloads > 0);
        console.log(activeSessions);
        return activeSessions;
    },
});

/** Subscribe to and process download session updates from backend **/
export async function initializeDownloadSessionState() {
    if (downloadSessionState.isLoaded) return;

    // Fetch initial configuration data
    downloadSessionState.all = await BandwidthMonitoringEndpoint.getActiveSessions();

    // Subscribe to real-time updates
    downloadSessionState.subscription = BandwidthMonitoringEndpoint.subscribe().onNext((downloadSessionUpdate: SessionStatsDto[][]) => {
        downloadSessionUpdate.forEach((updateBatch: SessionStatsDto[]) => {
            downloadSessionState.all = updateBatch;
        });
    });
}