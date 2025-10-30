import {proxy} from 'valtio';
import {BandwidthMonitoringEndpoint} from "Frontend/generated/endpoints";
import SessionStatsDto from "Frontend/generated/org/gameyfin/app/core/download/bandwidth/SessionStatsDto";
import {Subscription} from "@vaadin/hilla-frontend";
import {convertBpsToMbps} from "Frontend/util/utils";

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
        return this.all.reduce((total: number, session: SessionStatsDto) => total + session.currentBytesPerSecond, 0);
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

/** Sort sessions: active sessions (by bandwidth, then oldest first), inactive sessions (newest first) **/
function sortSessions(sessions: SessionStatsDto[]): SessionStatsDto[] {
    return [...sessions].sort((a, b) => {
        const aIsActive = a.activeDownloads > 0;
        const bIsActive = b.activeDownloads > 0;

        // Active sessions come first
        if (aIsActive !== bIsActive) {
            return bIsActive ? 1 : -1;
        }

        // For active sessions: sort by bandwidth (highest first), then by age (oldest first)
        if (aIsActive) {
            const bandwidthDiff = convertBpsToMbps(b.currentBytesPerSecond, 0) - convertBpsToMbps(a.currentBytesPerSecond, 0);
            if (bandwidthDiff !== 0) {
                return bandwidthDiff;
            }

            // Tie breaker: oldest first
            const aTime = new Date(a.startTime).getTime();
            const bTime = new Date(b.startTime).getTime();
            return aTime - bTime;
        }

        // For inactive sessions: sort by age (newest first)
        const aTime = new Date(a.startTime).getTime();
        const bTime = new Date(b.startTime).getTime();
        return bTime - aTime;
    });
}