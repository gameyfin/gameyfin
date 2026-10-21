import {proxy} from "valtio";
import {CollectionEndpoint} from "Frontend/generated/endpoints";
import CollectionDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionDto";
import CollectionEvent from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionEvent";
import CollectionField from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionField";
import CollectionPatchDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionPatchDto";
import {createSyncedStore, SyncedEntityState} from "Frontend/state/sync/createSyncedStore";
import {identityMapping} from "Frontend/state/sync/patches";

/** Collections are small, so there is no separate full tier */
type CollectionState = SyncedEntityState<CollectionDto, CollectionDto> & {
    collections: CollectionDto[];
    sorted: CollectionDto[];
};

export const collectionState = proxy<CollectionState>({
    state: {},
    full: {},
    isLoaded: false,
    isSynced: false,
    get collections() {
        return Object.values<CollectionDto>(this.state);
    },
    get sorted() {
        return Object.values<CollectionDto>(this.state).sort((a, b) => {
            const orderA = a.metadata?.displayOrder ?? -1;
            const orderB = b.metadata?.displayOrder ?? -1;

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

const collectionPatchMapping = identityMapping<CollectionField>({
    [CollectionField.NAME]: "name",
    [CollectionField.DESCRIPTION]: "description",
    [CollectionField.GAME_IDS]: "gameIds",
    [CollectionField.METADATA]: "metadata"
});

export const collectionStore = createSyncedStore<CollectionDto, CollectionDto, CollectionEvent, CollectionField, CollectionPatchDto>(collectionState, {
    name: "collections",
    summaryStore: "collections",
    fetchSync: async (since) => {
        const result = await CollectionEndpoint.sync(since);
        return {serverTime: result.serverTime, full: result.full, items: result.collections, allIds: result.allIds};
    },
    subscribe: () => CollectionEndpoint.subscribeToCollectionEvents(),
    normalizeEvent: (event) => ({type: event.type, id: event.id, summary: event.collection, patch: event.patch}),
    patchMapping: collectionPatchMapping
});

/** Subscribe to and process state updates from backend **/
export async function initializeCollectionState(identity: string) {
    await collectionStore.start(identity);
}
