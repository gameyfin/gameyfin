import React from "react";
import {Chip} from "@heroui/react";
import {StarIcon} from "@phosphor-icons/react";

interface StoreVersionLike {
    id: number;
    branch: string;
    label: string;
    releasedAt?: string;
}

/** The newest build of the main (production) branch. Other branches such as betas are skipped, even if newer. */
function latestProduction(versions: StoreVersionLike[]): StoreVersionLike | undefined {
    let latest: StoreVersionLike | undefined;
    for (const version of versions) {
        if (version.branch !== "") continue;
        if (!latest || (version.releasedAt ?? "") > (latest.releasedAt ?? "")) latest = version;
    }
    return latest;
}

export interface VersionMarkers {
    latestProductionId?: number;
    /** IDs of builds released after the latest production build, typically betas or test branches */
    newerThanProductionIds: Set<number>;
}

export function versionMarkers(versions: StoreVersionLike[]): VersionMarkers {
    const latest = latestProduction(versions);
    const newer = new Set<number>();
    if (latest?.releasedAt) {
        for (const version of versions) {
            if (version.releasedAt && version.releasedAt > latest.releasedAt) newer.add(version.id);
        }
    }
    return {latestProductionId: latest?.id, newerThanProductionIds: newer};
}

/**
 * Label of a store version: a gold star marks the latest production version, other branches get a colored chip, and
 * builds newer than the latest production version have colored text.
 */
export default function VersionOptionLabel({version, markers}: {
    version: StoreVersionLike;
    markers: VersionMarkers;
}) {
    const newer = markers.newerThanProductionIds.has(version.id);
    return (
        <span className="inline-flex flex-wrap items-center gap-2">
            {version.id === markers.latestProductionId &&
                <StarIcon weight="fill" className="text-yellow-400" aria-label="Latest production version"/>}
            <span className={newer ? "text-warning" : undefined}
                  title={newer ? "Newer than the latest production version" : undefined}>
                {version.label}
            </span>
            {version.branch && <Chip size="sm" color="accent" variant="soft">{version.branch}</Chip>}
        </span>
    );
}
