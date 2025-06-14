import {proxy} from 'valtio';
import type LibraryScanProgress from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryScanProgress";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import {Subscription} from "@vaadin/hilla-frontend";
import LibraryScanStatus from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryScanStatus";
import {libraryState} from "Frontend/state/LibraryState";

type ScanState = {
    subscription?: Subscription<LibraryScanProgress[]>;
    state: Record<string, LibraryScanProgress>;
    hasContent: boolean,
    isScanning: boolean,
    sortedByStartTime: LibraryScanProgress[];
};

export const scanState = proxy<ScanState>({
    state: {},
    get hasContent(): boolean {
        return Object.values(this.state).length > 0;
    },
    get isScanning(): boolean {
        return Object.values(this.state)
            .some((scanProgress: LibraryScanProgress) => scanProgress.status === LibraryScanStatus.IN_PROGRESS);
    },
    get sortedByStartTime(): LibraryScanProgress[] {
        return Object.values(this.state).sort((a: LibraryScanProgress, b: LibraryScanProgress) => {
            return new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime();
        });
    }
});

/** Subscribe to and process state updates from backend **/
export function initializeScanState() {
    if (scanState.subscription) return;

    // Subscribe to real-time updates
    scanState.subscription = LibraryEndpoint.subscribeToScanProgressEvents().onNext((scanProgresses: LibraryScanProgress[]) => {
        scanProgresses.forEach((scanProgress: LibraryScanProgress) => {
            // Filter out scans for libraries that are not in the current state
            if (!libraryState.state[scanProgress.libraryId]) return;

            scanState.state[scanProgress.scanId] = scanProgress;
        })
    });
}

export function handleLibraryDeletion(libraryId: number) {
    for (const scanId in scanState.state) {
        if (scanState.state[scanId].libraryId === libraryId) {
            delete scanState.state[scanId];
        }
    }
}