import {proxy} from 'valtio';
import {BandwidthMonitoringEndpoint} from "Frontend/generated/endpoints";
import SessionStatsDto from "Frontend/generated/org/gameyfin/app/core/download/bandwidth/SessionStatsDto";
import {Subscription} from "@vaadin/hilla-frontend";

type DownloadSessionState = {
    subscription?: Subscription<SessionStatsDto[][]>;
    isLoaded: boolean;
    all: SessionStatsDto[];
    byId: Record<string, SessionStatsDto>;
    activeSessions: number;
    bandwidthInUse: number;
};

export const downloadSessionState = proxy<DownloadSessionState>({
    get isLoaded() {
        return this.subscription != null;
    },
    all: [],
    byId: {},
    get activeSessions() {
        return this.all.filter((session: SessionStatsDto) => session.activeDownloads > 0).length;
    },
    get bandwidthInUse() {
        return this.all.reduce((total: number, session: SessionStatsDto) => total + session.currentMbps);
    }
});

/** Subscribe to and process download session updates from backend **/
export async function initializeDownloadSessionState() {
    if (downloadSessionState.isLoaded) return;

    // Fetch initial configuration data
    const initialSessions = await BandwidthMonitoringEndpoint.getActiveSessions();
    downloadSessionState.all = sortSessions(initialSessions);
    initialSessions.forEach((session: SessionStatsDto) => {
        downloadSessionState.byId[session.sessionId] = session;
    });

    // Subscribe to real-time updates
    downloadSessionState.subscription = BandwidthMonitoringEndpoint.subscribe().onNext((downloadSessionUpdate: SessionStatsDto[][]) => {
        downloadSessionUpdate.forEach((updateBatch: SessionStatsDto[]) => {
            downloadSessionState.all = sortSessions(updateBatch);
            updateBatch.forEach((session: SessionStatsDto) => {
                downloadSessionState.byId[session.sessionId] = session;
            });
        });
    });
}

/** Sort sessions by current bandwidth (highest first), then by start time **/
function sortSessions(sessions: SessionStatsDto[]): SessionStatsDto[] {
    return [...sessions].sort((a, b) => {
        // Sort by current bandwidth (highest first), using integer comparison to avoid resorting for small fluctuations
        const bandwidthDiff = Math.trunc(b.currentMbps) - Math.trunc(a.currentMbps);
        if (bandwidthDiff !== 0) {
            return bandwidthDiff;
        }
        // Tie breaker: sort by start time (earliest first)
        return new Date(a.startTime).getTime() - new Date(b.startTime).getTime();
    });
}