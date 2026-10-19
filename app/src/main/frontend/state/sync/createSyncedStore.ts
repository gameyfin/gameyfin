import {ActionOnLostSubscription, Subscription} from "@vaadin/hilla-frontend";
import {snapshot, useSnapshot} from "valtio";
import {useEffect} from "react";
import EntityEventType from "Frontend/generated/org/gameyfin/app/core/sync/EntityEventType";
import {CachedEntity, entityCache, EntityStoreName} from "Frontend/state/sync/EntityCache";
import {applyPatch, EntityPatch, PatchMapping} from "Frontend/state/sync/patches";
import {compareTimestamps} from "Frontend/state/sync/timestamps";

const RESYNC_DELAY_MS = 500;
const RETRY_DELAY_MS = 10_000;

/** Valtio state of a synchronized entity type */
export interface SyncedEntityState<S extends CachedEntity, F extends CachedEntity> {
    /** Summary tier, always contains all entities */
    state: Record<number, S>;
    /** Full tier, only contains entities requested via `loadFull` */
    full: Record<number, F>;
    /** Whether data is available (either from the persistent cache or from the server) */
    isLoaded: boolean;
    /** Whether the data has been synchronized with the server at least once */
    isSynced: boolean;
}

export interface SyncResult<S> {
    serverTime: string;
    full: boolean;
    items: S[];
    allIds: number[];
}

export interface NormalizedEvent<S, P> {
    type: EntityEventType;
    id: number;
    summary?: S;
    patch?: P;
}

export interface SyncedStoreOptions<S extends CachedEntity, F extends CachedEntity, E, Field extends string, P extends EntityPatch<Field>> {
    /** Unique name, used as cache key */
    name: string;
    summaryStore: EntityStoreName;
    fullStore?: EntityStoreName;
    /** Fetches all entities changed since the given server timestamp, or all entities if `since` is undefined */
    fetchSync: (since: string | undefined) => Promise<SyncResult<S>>;
    fetchFull?: (id: number) => Promise<F | undefined>;
    subscribe: () => Subscription<E[]>;
    normalizeEvent: (event: E) => NormalizedEvent<S, P>;
    patchMapping: PatchMapping<Field>;
}

export interface SyncedStore<F> {
    /** Starts (or restarts, if the identity changed) synchronization */
    start(identity: string): Promise<void>;

    /** Returns the full DTO, from the in-memory tier, the persistent cache or the server (in this order) */
    loadFull(id: number, force?: boolean): Promise<F | undefined>;

    /** Requests a (delta) synchronization with the server */
    resync(): void;
}

/**
 * Creates a store which keeps the given valtio state synchronized with the backend:
 * 1. Hydrate from the persistent cache (IndexedDB)
 * 2. Subscribe to real-time events (buffered until step 3 is done)
 * 3. Fetch everything that changed since the last synchronization (delta sync)
 * 4. Apply real-time events: created entities are inserted, patches update only the changed fields,
 *    deleted entities are removed
 *
 * Patches older than the cached state are ignored, patches for unknown entities and reconnects trigger a delta sync.
 */
export function createSyncedStore<
    S extends CachedEntity,
    F extends CachedEntity,
    E,
    Field extends string,
    P extends EntityPatch<Field>
>(target: SyncedEntityState<S, F>, options: SyncedStoreOptions<S, F, E, Field, P>): SyncedStore<F> {
    const serverTimeKey = `serverTime:${options.name}`;

    let identity: string | undefined;
    let generation = 0;
    let subscription: Subscription<E[]> | undefined;
    let serverTime: string | undefined;
    /** Defined while events have to be buffered (until the initial synchronization is done) */
    let eventBuffer: E[] | undefined;
    let runningSync: Promise<void> | undefined;
    let syncRequested = false;
    let resyncTimer: ReturnType<typeof setTimeout> | undefined;
    let connectionLost = false;
    /** IDs changed by events while a synchronization request is in flight */
    let touchedDuringSync: Set<number> | undefined;
    let deletedDuringSync: Set<number> | undefined;
    const fullRequests = new Map<number, Promise<F | undefined>>();

    function reset() {
        subscription?.cancel();
        subscription = undefined;
        serverTime = undefined;
        eventBuffer = undefined;
        runningSync = undefined;
        syncRequested = false;
        clearTimeout(resyncTimer);
        resyncTimer = undefined;
        connectionLost = false;
        touchedDuringSync = undefined;
        deletedDuringSync = undefined;
        fullRequests.clear();

        target.state = {};
        target.full = {};
        target.isLoaded = false;
        target.isSynced = false;
    }

    async function start(newIdentity: string) {
        if (identity === newIdentity) return;

        reset();
        identity = newIdentity;
        const gen = ++generation;

        await entityCache.open(newIdentity);
        const [cachedItems, cachedServerTime] = await Promise.all([
            entityCache.getAll<S>(options.summaryStore),
            entityCache.getMeta(serverTimeKey)
        ]);
        if (gen !== generation) return;

        if (cachedServerTime) {
            serverTime = cachedServerTime;
            target.state = toRecord(cachedItems);
            target.isLoaded = true;
        }

        eventBuffer = [];
        let wasConnected = false;
        subscription = options.subscribe()
            .onNext((events: E[]) => {
                if (gen !== generation) return;
                if (eventBuffer) eventBuffer.push(...events);
                else handleEvents(events);
            })
            .onConnectionStateChange(event => {
                if (gen !== generation) return;
                if (event.detail.state !== "connected") {
                    connectionLost = wasConnected;
                } else if (!wasConnected) {
                    wasConnected = true;
                } else if (connectionLost) {
                    // Events might have been missed while disconnected
                    connectionLost = false;
                    scheduleResync(0);
                }
            })
            .onSubscriptionLost(() => {
                if (gen !== generation) return ActionOnLostSubscription.REMOVE;
                scheduleResync(0);
                return ActionOnLostSubscription.RESUBSCRIBE;
            });

        try {
            await sync(gen);
        } finally {
            if (gen === generation) {
                const buffered = eventBuffer ?? [];
                eventBuffer = undefined;
                handleEvents(buffered);
            }
        }
    }

    /** Runs a synchronization, or queues another run if one is already in progress */
    function sync(gen: number): Promise<void> {
        if (gen !== generation) return Promise.resolve();
        if (runningSync) {
            syncRequested = true;
            return runningSync;
        }

        let current: Promise<void> | undefined = undefined;
        current = (async () => {
            try {
                do {
                    syncRequested = false;
                    await doSync(gen);
                } while (syncRequested && gen === generation);
            } catch (e) {
                console.error(`Synchronization of ${options.name} failed`, e);
                if (gen === generation) scheduleResync(RETRY_DELAY_MS);
            } finally {
                if (runningSync === current) runningSync = undefined;
            }
        })();
        runningSync = current;
        return current;
    }

    async function doSync(gen: number) {
        const touched = touchedDuringSync = new Set();
        const deleted = deletedDuringSync = new Set();

        let result: SyncResult<S>;
        try {
            result = await options.fetchSync(serverTime);
        } finally {
            if (touchedDuringSync === touched) touchedDuringSync = undefined;
            if (deletedDuringSync === deleted) deletedDuringSync = undefined;
        }
        if (gen !== generation) return;

        if (result.full) {
            const next: Record<number, S> = {};
            for (const item of result.items) {
                if (!deleted.has(item.id)) next[item.id] = item;
            }
            // Keep newer versions received via events while the request was in flight
            for (const id of touched) {
                const current = target.state[id];
                if (current && (!next[id] || compareTimestamps(current.updatedAt, next[id].updatedAt) > 0)) {
                    next[id] = snapshot(current) as S;
                }
            }
            const removedIds = Object.keys(target.state).map(Number).filter(id => !(id in next));

            target.state = next;
            removeFull(removedIds);
            entityCache.clear(options.summaryStore);
            entityCache.put(options.summaryStore, Object.values(next), false);
        } else {
            const upserts: S[] = [];
            for (const item of result.items) {
                if (deleted.has(item.id)) continue;
                const current = target.state[item.id];
                if (!current || compareTimestamps(current.updatedAt, item.updatedAt) <= 0) {
                    target.state[item.id] = item;
                    upserts.push(item);
                }
            }
            entityCache.put(options.summaryStore, upserts);

            const existingIds = new Set(result.allIds);
            const removedIds = Object.keys(target.state)
                .map(Number)
                .filter(id => !existingIds.has(id) && !touched.has(id));
            remove(removedIds);
        }

        serverTime = result.serverTime;
        entityCache.setMeta(serverTimeKey, result.serverTime);
        target.isLoaded = true;
        target.isSynced = true;
    }

    function handleEvents(events: E[]) {
        const upserts: S[] = [];
        const fullUpserts: F[] = [];
        const removedIds: number[] = [];
        let unknownEntity = false;

        for (const rawEvent of events) {
            const event = options.normalizeEvent(rawEvent);
            const id = event.id;

            switch (event.type) {
                case EntityEventType.CREATED: {
                    if (!event.summary) break;
                    const current = target.state[id];
                    if (!current || compareTimestamps(current.updatedAt, event.summary.updatedAt) <= 0) {
                        target.state[id] = event.summary;
                        upserts.push(event.summary);
                    }
                    touchedDuringSync?.add(id);
                    break;
                }
                case EntityEventType.UPDATED: {
                    const patch = event.patch;
                    if (!patch) break;
                    const current = target.state[id];
                    if (!current) {
                        unknownEntity = true;
                        break;
                    }
                    // Outdated, e.g. if a synchronization already returned a newer state
                    if (compareTimestamps(patch.updatedAt, current.updatedAt) < 0) break;

                    const full = target.full[id];
                    const fullIsValid = full && compareTimestamps(full.updatedAt, current.updatedAt) >= 0;

                    const patched = applyPatch(snapshot(current) as S, patch, options.patchMapping, "summary");
                    target.state[id] = patched;
                    upserts.push(patched);

                    if (full && fullIsValid) {
                        const patchedFull = applyPatch(snapshot(full) as F, patch, options.patchMapping, "full");
                        target.full[id] = patchedFull;
                        fullUpserts.push(patchedFull);
                    } else if (full) {
                        delete target.full[id];
                    }
                    touchedDuringSync?.add(id);
                    break;
                }
                case EntityEventType.DELETED:
                    removedIds.push(id);
                    deletedDuringSync?.add(id);
                    break;
            }
        }

        entityCache.put(options.summaryStore, upserts);
        if (options.fullStore) entityCache.put(options.fullStore, fullUpserts);
        remove(removedIds);

        if (unknownEntity) scheduleResync();
    }

    function remove(ids: number[]) {
        if (ids.length === 0) return;
        for (const id of ids) delete target.state[id];
        removeFull(ids);
        entityCache.delete(options.summaryStore, ids);
    }

    function removeFull(ids: number[]) {
        if (ids.length === 0) return;
        for (const id of ids) delete target.full[id];
        if (options.fullStore) entityCache.delete(options.fullStore, ids);
    }

    function scheduleResync(delay = RESYNC_DELAY_MS) {
        if (identity === undefined) return;
        const gen = generation;
        clearTimeout(resyncTimer);
        resyncTimer = setTimeout(() => {
            resyncTimer = undefined;
            void sync(gen);
        }, delay);
    }

    function isFullValid(full: F | undefined, id: number): full is F {
        const summary = target.state[id];
        return !!full && !!summary && compareTimestamps(full.updatedAt, summary.updatedAt) >= 0;
    }

    function loadFull(id: number, force = false): Promise<F | undefined> {
        if (!options.fetchFull || !options.fullStore) return Promise.resolve(undefined);
        if (!force && isFullValid(target.full[id], id)) return Promise.resolve(target.full[id]);

        const pending = fullRequests.get(id);
        if (pending) return pending;

        const gen = generation;
        const request = (async () => {
            if (!force) {
                const cached = await entityCache.get<F>(options.fullStore!, id);
                if (gen !== generation) return undefined;
                if (isFullValid(cached, id)) {
                    target.full[id] = cached;
                    return cached;
                }
            }

            const dto = await options.fetchFull!(id);
            if (gen !== generation) return dto;

            if (dto) {
                target.full[id] = dto;
                entityCache.put(options.fullStore!, [dto]);
                // The summary is outdated (e.g. an event got lost)
                const summary = target.state[id];
                if (!summary || compareTimestamps(summary.updatedAt, dto.updatedAt) < 0) scheduleResync();
            } else if (target.isSynced) {
                remove([id]);
            }
            return dto;
        })().finally(() => {
            if (fullRequests.get(id) === request) fullRequests.delete(id);
        });

        fullRequests.set(id, request);
        return request;
    }

    return {
        start,
        loadFull,
        resync: () => scheduleResync(0)
    };
}

/**
 * Returns the full DTO of an entity, loading it if necessary.
 * While (re)loading, a previously loaded (possibly outdated) version is returned and `isStale` is `true`.
 */
export function useFullEntity<S extends CachedEntity, F extends CachedEntity>(
    target: SyncedEntityState<S, F>,
    store: SyncedStore<F>,
    id: number | undefined
): { full: F | undefined; isStale: boolean } {
    const snap = useSnapshot(target);
    const summary = id !== undefined ? snap.state[id] : undefined;
    const full = id !== undefined ? snap.full[id] as F | undefined : undefined;
    const isValid = !!full && !!summary && compareTimestamps(full.updatedAt, summary.updatedAt) >= 0;

    useEffect(() => {
        if (id === undefined || isValid) return;
        // Wait until we know whether the entity exists
        if (!summary && !snap.isSynced) return;
        void store.loadFull(id);
    }, [id, isValid, summary?.updatedAt, snap.isSynced]);

    return {full, isStale: !isValid};
}

function toRecord<T extends { id: number }>(items: T[]): Record<number, T> {
    const record: Record<number, T> = {};
    for (const item of items) record[item.id] = item;
    return record;
}
