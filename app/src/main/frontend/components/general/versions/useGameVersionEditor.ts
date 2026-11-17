import {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {toast} from "@heroui/react";
import {GameDlcEndpoint, GameStoreEndpoint, GameStoreLinkEndpoint, GameVersionEndpoint} from "Frontend/generated/endpoints";
import IncludedDlcDto from "Frontend/generated/org/gameyfin/app/games/versions/IncludedDlcDto";
import GameStoreDto from "Frontend/generated/org/gameyfin/app/games/versions/GameStoreDto";

type VersionStatus = Awaited<ReturnType<typeof GameVersionEndpoint.getStatus>>[number];

export type StoreVersionOption = VersionStatus["versions"][number];

/** A store link as shown in the editor. Edits are staged here and only written to the server by [apply]. */
export interface StagedLink {
    key: string;
    /** Undefined for links that were added in the editor but not saved yet. */
    linkId?: number;
    /** ID of the store, as declared by its plugin */
    store: string;
    storeName: string;
    /** False if the plugin of the store is disabled. Such links are read-only. */
    storeAvailable: boolean;
    storeAppId: string;
    title?: string;
    tracked: boolean;
    originalTracked: boolean;
    versions: StoreVersionOption[];
    versionsCheckedAt?: string;
    installedVersionId?: number;
    originalInstalledVersionId?: number;
    /** The staged library version was detected instead of selected by hand. */
    detected: boolean;
    removed: boolean;
    /** Free-text edition label such as "Anniversary Edition". */
    edition: string;
    originalEdition: string;
    /** DLC that is included in the installer(s) for this store product. */
    dlc: IncludedDlcDto[];
    originalDlc: IncludedDlcDto[];
    /** Versions of a link that is not saved yet use negative IDs, which index into this list. */
    previewBuilds?: { buildId: string; branch: string }[];
    loadingVersions?: boolean;
}

function errorMessage(error: unknown) {
    return error instanceof Error ? error.message : "Please try again.";
}

const sameDlc = (a: IncludedDlcDto[], b: IncludedDlcDto[]) =>
    a.length === b.length
    && a.every((item) => b.some((other) => other.dlcId === item.dlcId && other.detected === item.detected && other.kind === item.kind));

function fromStatus(status: VersionStatus, dlc: IncludedDlcDto[] = []): StagedLink {
    return {
        key: `link-${status.linkId}`,
        linkId: status.linkId,
        store: status.store,
        storeName: status.storeName,
        storeAvailable: status.storeAvailable,
        storeAppId: status.storeAppId,
        tracked: status.tracked,
        originalTracked: status.tracked,
        versions: status.versions,
        versionsCheckedAt: status.versionsCheckedAt,
        installedVersionId: status.installedVersionId,
        originalInstalledVersionId: status.installedVersionId,
        detected: status.installedVersionSource === "AUTO",
        removed: false,
        edition: status.edition ?? "",
        originalEdition: status.edition ?? "",
        dlc,
        originalDlc: dlc,
    };
}

async function loadLinks(gameId: number): Promise<StagedLink[]> {
    const [statuses, included] = await Promise.all([
        GameVersionEndpoint.getStatus(gameId),
        GameDlcEndpoint.getIncluded(gameId).catch(() => []),
    ]);
    return statuses.map((status) =>
        fromStatus(status, included.find((entry) => entry.linkId === status.linkId)?.dlc ?? []));
}

export function useGameVersionEditor(gameId: number, isOpen: boolean) {
    const [links, setLinks] = useState<StagedLink[]>([]);
    /** Stores of the active plugins. Version tracking needs at least one. */
    const [stores, setStores] = useState<GameStoreDto[]>([]);
    const [loading, setLoading] = useState(false);
    const [pendingKey, setPendingKey] = useState<string>();
    const newCounter = useRef(0);

    // Reloading when the dialog opens discards edits that were cancelled
    useEffect(() => {
        if (!isOpen) return;
        let cancelled = false;
        setLoading(true);
        Promise.all([loadLinks(gameId), GameStoreEndpoint.getStores()])
            .then(([loaded, availableStores]) => {
                if (cancelled) return;
                setLinks(loaded);
                setStores(availableStores);
            })
            .catch((error) => toast.danger("Could not load game versions", {description: errorMessage(error)}))
            .finally(() => {
                if (!cancelled) setLoading(false);
            });
        return () => {
            cancelled = true;
        };
    }, [gameId, isOpen]);

    const visibleLinks = useMemo(() => links.filter((link) => !link.removed), [links]);

    const dirty = useMemo(() => links.some((link) =>
        link.linkId === undefined
            ? !link.removed
            : link.removed
            || link.installedVersionId !== link.originalInstalledVersionId
            || link.tracked !== link.originalTracked
            || !sameDlc(link.dlc, link.originalDlc)
            || link.edition.trim() !== link.originalEdition
    ), [links]);

    const update = useCallback((key: string, change: Partial<StagedLink>) => {
        setLinks((current) => current.map((link) => link.key === key ? {...link, ...change} : link));
    }, []);

    /** Fetches the versions of a store product that is not linked yet, without saving anything. */
    const loadPreview = useCallback(async (key: string, store: string, storeAppId: string, overrideSelection = false) => {
        update(key, {loadingVersions: true});
        try {
            const preview = await GameVersionEndpoint.previewVersions(gameId, store, storeAppId);
            const versions = preview.versions.map((version, index) => ({
                id: -(index + 1),
                buildId: version.buildId,
                branch: version.branch,
                label: version.label,
                releasedAt: version.releasedAt,
            }));
            setLinks((current) => current.map((link) => {
                if (link.key !== key) return link;
                const detectedId = preview.detectedIndex == null ? undefined : -(preview.detectedIndex + 1);
                const keepSelection = link.installedVersionId !== undefined && (overrideSelection ? detectedId === undefined : !link.detected);
                return {
                    ...link,
                    versions,
                    previewBuilds: preview.versions.map(({buildId, branch}) => ({buildId, branch})),
                    installedVersionId: keepSelection ? link.installedVersionId : detectedId,
                    detected: keepSelection ? link.detected : detectedId !== undefined,
                    loadingVersions: false,
                };
            }));
            if (preview.detectedIndex != null) {
                toast.success("Library version detected", {description: preview.versions[preview.detectedIndex].label});
            } else if (overrideSelection) {
                toast.info("Version not detected", {description: "No library version matching the cached builds was detected"});
            }
        } catch (error) {
            update(key, {loadingVersions: false});
            toast.warning("Could not fetch versions", {
                description: errorMessage(error) + " You can still add the store and use Refresh after saving.",
            });
        }
    }, [gameId, update]);

    const addLink = useCallback((store: string, storeAppId: string, title?: string, edition = "") => {
        const duplicate = links.some((link) => !link.removed && link.store === store && link.storeAppId === storeAppId);
        if (duplicate) return;

        const removedMatch = links.find((link) => link.removed && link.store === store && link.storeAppId === storeAppId);
        if (removedMatch) {
            update(removedMatch.key, {removed: false});
            return;
        }

        const key = `new-${newCounter.current++}`;
        setLinks((current) => [...current, {
            key, store, storeName: stores.find((candidate) => candidate.id === store)?.displayName ?? store,
            storeAvailable: true, storeAppId, title,
            tracked: false, originalTracked: false,
            versions: [], detected: false, removed: false, edition, originalEdition: "", dlc: [], originalDlc: [],
        }]);
        void loadPreview(key, store, storeAppId);
    }, [links, stores, loadPreview, update]);

    const refreshPreview = useCallback((key: string) => {
        const link = links.find((candidate) => candidate.key === key);
        if (link && link.linkId === undefined) void loadPreview(key, link.store, link.storeAppId);
    }, [links, loadPreview]);

    const removeLink = useCallback((key: string) => {
        setLinks((current) => current
            .filter((link) => !(link.key === key && link.linkId === undefined))
            .map((link) => link.key === key ? {...link, removed: true} : link));
    }, []);

    const setInstalledVersion = useCallback((key: string, versionId?: number) => {
        update(key, {installedVersionId: versionId, detected: false});
    }, [update]);

    const setDlc = useCallback((key: string, dlc: IncludedDlcDto[]) => {
        update(key, {dlc});
    }, [update]);

    const setEdition = useCallback((key: string, edition: string) => {
        update(key, {edition});
    }, [update]);

    const makePrimary = useCallback((key: string) => {
        setLinks((current) => current.map((link) => ({...link, tracked: link.key === key})));
    }, []);

    const detect = useCallback(async (key: string) => {
        const link = links.find((candidate) => candidate.key === key);
        if (!link) return;
        if (link.linkId === undefined) {
            await loadPreview(link.key, link.store, link.storeAppId, true);
            return;
        }
        setPendingKey(key);
        try {
            const result = await GameVersionEndpoint.previewDetection(link.linkId);
            if (result.detected) {
                update(key, {installedVersionId: result.installedVersionId, detected: true});
                toast.success("Library version detected", {description: result.message});
            } else {
                toast.info("Version not detected", {description: result.message});
            }
        } catch (error) {
            toast.danger("Could not detect library version", {description: errorMessage(error)});
        } finally {
            setPendingKey(undefined);
        }
    }, [links, update, loadPreview]);

    /** Refreshing only updates the cached store versions, so it takes effect immediately. */
    const refresh = useCallback(async (key: string) => {
        const link = links.find((candidate) => candidate.key === key);
        if (!link) return;
        if (link.linkId === undefined) {
            await loadPreview(link.key, link.store, link.storeAppId, true);
            return;
        }
        setPendingKey(key);
        try {
            await GameVersionEndpoint.refreshVersions(link.linkId);
            const status = (await GameVersionEndpoint.getStatus(gameId)).find((s) => s.linkId === link.linkId);
            if (status) update(key, {versions: status.versions, versionsCheckedAt: status.versionsCheckedAt});
            toast.success("Versions refreshed");
        } catch (error) {
            toast.danger("Could not refresh versions", {description: errorMessage(error)});
        } finally {
            setPendingKey(undefined);
        }
    }, [gameId, links, update]);

    /** Writes all staged edits to the server. Throws if a step fails, after reloading the actual state. */
    const apply = useCallback(async () => {
        if (!dirty) return;
        try {
            for (const link of links.filter((l) => l.removed && l.linkId !== undefined)) {
                await GameStoreLinkEndpoint.removeLink(link.linkId!);
            }

            for (const link of links.filter((l) => l.linkId === undefined && !l.removed)) {
                const created = await GameStoreLinkEndpoint.addLink(gameId, link.store, link.storeAppId);
                try {
                    await GameVersionEndpoint.refreshVersions(created.id);
                    const staged = link.previewBuilds?.[-(link.installedVersionId ?? 0) - 1];
                    if (staged) {
                        const status = (await GameVersionEndpoint.getStatus(gameId)).find((s) => s.linkId === created.id);
                        const match = status?.versions.find((v) => v.buildId === staged.buildId && v.branch === staged.branch);
                        if (match) await GameVersionEndpoint.setInstalledVersion(created.id, match.id, link.detected);
                    } else if (link.installedVersionId === undefined && !link.previewBuilds) {
                        // The versions could not be previewed, so detection is the best remaining option
                        await GameVersionEndpoint.detectInstalledVersion(created.id);
                    }
                } catch (error) {
                    toast.warning(`${link.storeName} was added, but its versions could not be fetched`, {
                        description: errorMessage(error) + " Use Refresh to try again.",
                    });
                }
                if (link.edition.trim()) await GameStoreLinkEndpoint.setEdition(created.id, link.edition.trim());
                if (link.dlc.length > 0) await GameDlcEndpoint.setIncluded(created.id, link.dlc);
            }

            const existing = links.filter((l) => l.linkId !== undefined && !l.removed);
            const newPrimary = existing.find((l) => l.tracked && !l.originalTracked);
            if (newPrimary) await GameStoreLinkEndpoint.setTracked(newPrimary.linkId!);

            for (const link of existing.filter((l) => l.installedVersionId !== l.originalInstalledVersionId)) {
                await GameVersionEndpoint.setInstalledVersion(link.linkId!, link.installedVersionId, link.detected);
            }

            for (const link of existing.filter((l) => l.edition.trim() !== l.originalEdition)) {
                await GameStoreLinkEndpoint.setEdition(link.linkId!, link.edition.trim() || undefined);
            }

            for (const link of existing.filter((l) => !sameDlc(l.dlc, l.originalDlc))) {
                await GameDlcEndpoint.setIncluded(link.linkId!, link.dlc);
            }
        } catch (error) {
            toast.danger("Could not save store versions", {description: errorMessage(error)});
            setLinks(await loadLinks(gameId));
            throw error;
        }
    }, [dirty, gameId, links]);

    return {
        links: visibleLinks, stores, available: stores.length > 0, loading, pendingKey, dirty,
        addLink, removeLink, setInstalledVersion, setDlc, setEdition, makePrimary, detect, refresh, refreshPreview, apply,
    };
}

export type GameVersionEditorState = ReturnType<typeof useGameVersionEditor>;
