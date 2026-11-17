import React, {useEffect, useState} from "react";
import {Button, Chip} from "@heroui/react";
import {GameDlcEndpoint, GameVersionEndpoint} from "Frontend/generated/endpoints";
import DlcKind from "Frontend/generated/org/gameyfin/app/games/versions/DlcKind";
import GameIncludedDlcDto from "Frontend/generated/org/gameyfin/app/games/versions/GameIncludedDlcDto";

interface GameVersionDetailsProps {
    gameId: number;
    refreshKey?: unknown;
}

type VersionStatus = Awaited<ReturnType<typeof GameVersionEndpoint.getStatus>>[number];

const DLC_PREVIEW_COUNT = 3;

const EXTRA_ROWS = [
    {kind: DlcKind.EDITION_UPGRADE, label: "Edition upgrades"},
    {kind: DlcKind.DLC, label: "DLC"},
    {kind: DlcKind.BONUS, label: "Bonus material"},
];

function ExtrasRow({label, titles}: { label: string; titles: string[] }) {
    const [expanded, setExpanded] = useState(false);
    if (titles.length === 0) return null;
    return (
        <tr>
            <td className="text-muted w-0 min-w-32">{label}</td>
            <td>
                <div className="flex flex-wrap items-center gap-1">
                    {titles.slice(0, DLC_PREVIEW_COUNT).map((title) => <Chip key={title} size="sm">{title}</Chip>)}
                    {titles.length > DLC_PREVIEW_COUNT && (
                        <Button size="sm" variant="ghost" onPress={() => setExpanded(!expanded)}>
                            {expanded ? "Show less" : `+${titles.length - DLC_PREVIEW_COUNT} more`}
                        </Button>
                    )}
                </div>
                {expanded && (
                    <ul className="mt-2 max-h-60 list-disc overflow-y-auto pl-5">
                        {titles.slice(DLC_PREVIEW_COUNT).map((title) => <li key={title}>{title}</li>)}
                    </ul>
                )}
            </td>
        </tr>
    );
}

/** Read-only table rows for the game details table. Renders nothing if no store is linked. */
export default function GameVersionDetails({gameId, refreshKey}: GameVersionDetailsProps) {
    const [statuses, setStatuses] = useState<VersionStatus[]>([]);
    const [included, setIncluded] = useState<GameIncludedDlcDto[]>([]);

    useEffect(() => {
        GameVersionEndpoint.getStatus(gameId).then(setStatuses).catch(() => setStatuses([]));
        GameDlcEndpoint.getIncluded(gameId).then(setIncluded).catch(() => setIncluded([]));
    }, [gameId, refreshKey]);

    return (
        <>
            {statuses.map((status) => {
                const installed = status.versions.find((version) => version.id === status.installedVersionId);
                const latest = status.versions.find((version) => version.id === status.latestVersionId);
                return (
                    <tr key={status.linkId}>
                        <td className="text-muted w-0 min-w-32">{status.storeName} version</td>
                        <td>
                            <div className="flex flex-wrap items-center gap-2">
                                <span>{installed?.label ?? "Unknown"}</span>
                                {status.edition && <Chip size="sm" variant="soft">{status.edition}</Chip>}
                                {status.updateAvailable === true &&
                                    <Chip color="warning" size="sm">Update available</Chip>}
                                {status.updateAvailable === false &&
                                    <Chip color="success" size="sm">Up to date</Chip>}
                                {latest && status.updateAvailable === true &&
                                    <span className="text-muted">Latest: {latest.label}</span>}
                            </div>
                        </td>
                    </tr>
                );
            })}
            {EXTRA_ROWS.map(({kind, label}) => (
                <ExtrasRow key={kind} label={label}
                           titles={[...new Set(included.flatMap((entry) => entry.dlc
                               .filter((item) => item.kind === kind).map((item) => item.title)))]}/>
            ))}
        </>
    );
}
