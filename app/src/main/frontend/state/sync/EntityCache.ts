import {DBSchema, IDBPDatabase, IDBPTransaction, openDB} from "idb";
import * as PackageJson from "../../../../../package.json";
import {compareTimestamps} from "Frontend/state/sync/timestamps";

/**
 * Bump this whenever the shape of a cached DTO changes in an incompatible way.
 * All caches with a different version are wiped on startup.
 */
const CACHE_SCHEMA_VERSION = 1;

const DB_NAME = "gameyfin-cache";
/** IndexedDB version, only needs to be bumped when object stores are added/removed */
const DB_VERSION = 1;
const WRITE_DELAY_MS = 250;

export type EntityStoreName = "games" | "gamesFull" | "libraries" | "librariesFull" | "collections";
const ENTITY_STORES: EntityStoreName[] = ["games", "gamesFull", "libraries", "librariesFull", "collections"];

export interface CachedEntity {
    id: number;
    updatedAt?: string;
}

interface CacheSchema extends DBSchema {
    meta: { key: string; value: string };
    games: { key: number; value: CachedEntity };
    gamesFull: { key: number; value: CachedEntity };
    libraries: { key: number; value: CachedEntity };
    librariesFull: { key: number; value: CachedEntity };
    collections: { key: number; value: CachedEntity };
}

type WriteOp =
    | { kind: "put"; store: EntityStoreName; items: CachedEntity[]; onlyIfNewer: boolean }
    | { kind: "delete"; store: EntityStoreName; ids: number[] }
    | { kind: "clear"; store: EntityStoreName }
    | { kind: "meta"; key: string; value: string };

type WriteTransaction = IDBPTransaction<CacheSchema, ("meta" | EntityStoreName)[], "readwrite">;

/**
 * Persistent client-side cache for entities, backed by IndexedDB.
 *
 * The cache is bound to an identity (user, roles, app version and cache schema version) and is wiped whenever the
 * identity changes, so no data leaks between users and no stale data survives updates.
 *
 * Writes are queued and executed in a single transaction, so entity data and sync timestamps are always persisted
 * atomically. If IndexedDB is unavailable (e.g. private browsing), the cache silently degrades to a no-op.
 */
class EntityCache {
    private db?: IDBPDatabase<CacheSchema>;
    private identity?: string;
    private opening?: Promise<void>;
    private queue: WriteOp[] = [];
    private flushTimer?: ReturnType<typeof setTimeout>;
    private flushing: Promise<void> = Promise.resolve();

    constructor() {
        if (typeof window !== "undefined") {
            window.addEventListener("pagehide", () => this.flush());
            document.addEventListener("visibilitychange", () => {
                if (document.visibilityState === "hidden") this.flush();
            });
        }
    }

    /** Opens the cache for the given identity, wiping it if it belongs to a different identity */
    open(identity: string): Promise<void> {
        const fullIdentity = `${identity}|${PackageJson.version}|${CACHE_SCHEMA_VERSION}`;
        if (this.identity === fullIdentity && this.opening) return this.opening;

        this.identity = fullIdentity;
        // Pending writes belong to the previous identity
        this.queue = [];
        this.opening = this.doOpen(fullIdentity);
        return this.opening;
    }

    private async doOpen(identity: string) {
        try {
            this.db ??= await openDB<CacheSchema>(DB_NAME, DB_VERSION, {
                upgrade(db) {
                    if (!db.objectStoreNames.contains("meta")) db.createObjectStore("meta");
                    for (const store of ENTITY_STORES) {
                        if (!db.objectStoreNames.contains(store)) db.createObjectStore(store, {keyPath: "id"});
                    }
                },
                blocking: () => {
                    // Another tab wants to upgrade the database
                    this.db?.close();
                    this.db = undefined;
                }
            });

            const cachedIdentity = await this.db.get("meta", "identity");
            if (cachedIdentity !== identity) {
                await this.clearAll(this.db);
                await this.db.put("meta", identity, "identity");
            }
        } catch (e) {
            console.warn("Entity cache unavailable, continuing without persistent cache", e);
            this.db = undefined;
        }
    }

    private async clearAll(db: IDBPDatabase<CacheSchema>) {
        const tx = db.transaction(["meta", ...ENTITY_STORES], "readwrite");
        await Promise.all([
            tx.objectStore("meta").clear(),
            ...ENTITY_STORES.map(store => tx.objectStore(store).clear()),
            tx.done
        ]);
    }

    /** Wipes all cached data, e.g. on logout */
    async wipe() {
        this.queue = [];
        this.identity = undefined;
        this.opening = undefined;
        try {
            this.db ??= await openDB<CacheSchema>(DB_NAME, DB_VERSION);
            await this.clearAll(this.db);
        } catch (e) {
            console.warn("Could not wipe entity cache", e);
        }
    }

    async getAll<T extends CachedEntity>(store: EntityStoreName): Promise<T[]> {
        await this.opening;
        try {
            return (await this.db?.getAll(store) ?? []) as T[];
        } catch (e) {
            console.warn(`Could not read ${store} from cache`, e);
            return [];
        }
    }

    async get<T extends CachedEntity>(store: EntityStoreName, id: number): Promise<T | undefined> {
        await this.opening;
        try {
            return await this.db?.get(store, id) as T | undefined;
        } catch (e) {
            return undefined;
        }
    }

    async getMeta(key: string): Promise<string | undefined> {
        await this.opening;
        try {
            return await this.db?.get("meta", key);
        } catch (e) {
            return undefined;
        }
    }

    /**
     * Queues writing the given entities.
     * @param onlyIfNewer Skip entities if the cache already contains a newer version (e.g. written by another tab)
     */
    put(store: EntityStoreName, items: CachedEntity[], onlyIfNewer = true) {
        if (items.length === 0) return;
        // Copied immediately: valtio wraps objects passed to it in place, proxies can't be stored in IndexedDB
        this.enqueue({kind: "put", store, items: items.map(toPlain), onlyIfNewer});
    }

    delete(store: EntityStoreName, ids: number[]) {
        if (ids.length === 0) return;
        this.enqueue({kind: "delete", store, ids});
    }

    clear(store: EntityStoreName) {
        this.enqueue({kind: "clear", store});
    }

    setMeta(key: string, value: string) {
        this.enqueue({kind: "meta", key, value});
    }

    private enqueue(op: WriteOp) {
        if (!this.identity) return;
        this.queue.push(op);
        this.flushTimer ??= setTimeout(() => this.flush(), WRITE_DELAY_MS);
    }

    /** Writes all queued operations within a single transaction */
    flush(): Promise<void> {
        if (this.flushTimer) {
            clearTimeout(this.flushTimer);
            this.flushTimer = undefined;
        }
        if (this.queue.length === 0) return this.flushing;

        const ops = this.queue;
        const identity = this.identity;
        this.queue = [];

        this.flushing = this.flushing.then(async () => {
            await this.opening;
            if (!this.db || identity !== this.identity) return;
            try {
                const tx = this.db.transaction(["meta", ...ENTITY_STORES], "readwrite");
                for (const op of ops) await this.apply(tx, op);
                await tx.done;
            } catch (e) {
                console.warn("Could not write to entity cache", e);
            }
        });
        return this.flushing;
    }

    private async apply(tx: WriteTransaction, op: WriteOp) {
        switch (op.kind) {
            case "put": {
                const store = tx.objectStore(op.store);
                if (!op.onlyIfNewer) {
                    await Promise.all(op.items.map(item => store.put(item)));
                    return;
                }
                await Promise.all(op.items.map(async item => {
                    const existing = await store.get(item.id);
                    if (!existing || compareTimestamps(existing.updatedAt, item.updatedAt) <= 0) {
                        await store.put(item);
                    }
                }));
                return;
            }
            case "delete": {
                const store = tx.objectStore(op.store);
                await Promise.all(op.ids.map(id => store.delete(id)));
                return;
            }
            case "clear":
                await tx.objectStore(op.store).clear();
                return;
            case "meta":
                await tx.objectStore("meta").put(op.value, op.key);
                return;
        }
    }
}

/** Deep copy of JSON-like data, which also unwraps (nested) valtio proxies and snapshots */
function toPlain<T>(value: T): T {
    if (value === null || typeof value !== "object") return value;
    if (Array.isArray(value)) return value.map(toPlain) as T;
    const copy: Record<string, unknown> = {};
    for (const key of Object.keys(value)) copy[key] = toPlain((value as Record<string, unknown>)[key]);
    return copy as T;
}

export const entityCache = new EntityCache();
