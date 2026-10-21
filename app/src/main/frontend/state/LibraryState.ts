import {proxy} from "valtio";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import LibrarySummaryDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibrarySummaryDto";
import LibraryAdminDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryAdminDto";
import LibraryEvent from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryEvent";
import LibraryField from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryField";
import LibraryPatchDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryPatchDto";
import {createSyncedStore, SyncedEntityState, useFullEntity} from "Frontend/state/sync/createSyncedStore";
import {identityMapping} from "Frontend/state/sync/patches";

type LibraryState = SyncedEntityState<LibrarySummaryDto, LibraryAdminDto> & {
    libraries: LibrarySummaryDto[];
    sorted: LibrarySummaryDto[];
};

export const libraryState = proxy<LibraryState>({
    state: {},
    full: {},
    isLoaded: false,
    isSynced: false,
    get libraries() {
        return Object.values<LibrarySummaryDto>(this.state);
    },
    get sorted() {
        return Object.values<LibrarySummaryDto>(this.state).sort((a, b) => {
            const orderA = a.metadata.displayOrder;
            const orderB = b.metadata.displayOrder;

            // Handle -1 as "end of list"
            const effectiveOrderA = orderA === -1 ? Number.MAX_SAFE_INTEGER : orderA;
            const effectiveOrderB = orderB === -1 ? Number.MAX_SAFE_INTEGER : orderB;

            const orderDiff = effectiveOrderA - effectiveOrderB;
            if (orderDiff !== 0) {
                return orderDiff;
            }

            // Fallback to creation date (newer first)
            return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
        });
    }
});

const libraryPatchMapping = identityMapping<LibraryField>(
    {
        [LibraryField.NAME]: "name",
        [LibraryField.METADATA]: "metadata",
        [LibraryField.PLATFORMS]: "platforms",
        [LibraryField.DIRECTORIES]: "directories",
        [LibraryField.IGNORED_PATHS]: "ignoredPaths"
    },
    {summary: [LibraryField.NAME, LibraryField.METADATA, LibraryField.PLATFORMS]}
);

export const libraryStore = createSyncedStore<LibrarySummaryDto, LibraryAdminDto, LibraryEvent, LibraryField, LibraryPatchDto>(libraryState, {
    name: "libraries",
    summaryStore: "libraries",
    fullStore: "librariesFull",
    fetchSync: async (since) => {
        const result = await LibraryEndpoint.sync(since);
        return {serverTime: result.serverTime, full: result.full, items: result.libraries, allIds: result.allIds};
    },
    // Only available for admins
    fetchFull: (id) => LibraryEndpoint.getById(id),
    subscribe: () => LibraryEndpoint.subscribeToLibraryEvents(),
    normalizeEvent: (event) => event,
    patchMapping: libraryPatchMapping
});

/** Subscribe to and process state updates from backend **/
export async function initializeLibraryState(identity: string) {
    await libraryStore.start(identity);
}

/** Returns the full (admin) DTO of a library, loaded on demand. Only available for admins. */
export function useFullLibrary(libraryId: number | undefined) {
    return useFullEntity(libraryState, libraryStore, libraryId);
}
