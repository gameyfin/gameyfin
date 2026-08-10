import {
    Button,
    Link,
    Popover,
    ProgressBar,
    ScrollShadow,
    Separator,
    Spinner
} from "@heroui/react";
import {useSnapshot} from "valtio/react";
import {scanState} from "Frontend/state/ScanState";
import {libraryState} from "Frontend/state/LibraryState";
import {TargetIcon, WarningIcon} from "@phosphor-icons/react";
import {timeBetween, timeUntil, toTitleCase} from "Frontend/util/utils";
import LibraryScanStatus from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryScanStatus";
import type LibraryScanProgress from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryScanProgress";
import {useEffect, useRef, useState} from "react";

export default function ScanProgressPopover() {
    const libraries = useSnapshot(libraryState).state;
    const scans = useSnapshot(scanState).sortedByStartTime;
    const scanInProgress = useSnapshot(scanState).isScanning;

    // Add state to track current time and force re-renders
    const [_currentTime, setCurrentTime] = useState(Date.now());

    // Cache ETAs per scanId: { eta: string | null, computedAt: number }
    const etaCacheRef = useRef<Record<string, { eta: string | null; computedAt: number }>>({});

    // Set up an interval to update the time every second
    useEffect(() => {
        const intervalId = setInterval(() => {
            setCurrentTime(Date.now());
        }, 1000);

        // Clean up the interval when component unmounts
        return () => clearInterval(intervalId);
    }, []);

    function estimateTimeLeft(scan: LibraryScanProgress): string | null {
        const now = Date.now();
        const cached = etaCacheRef.current[scan.scanId];
        // Only recompute every 5 seconds
        if (cached && now - cached.computedAt < 5000) {
            return cached.eta;
        }

        const current = scan.currentStep.current;
        const total = scan.currentStep.total;
        if (!current || !total || current <= 0 || total <= 0) {
            etaCacheRef.current[scan.scanId] = {eta: null, computedAt: now};
            return null;
        }

        const elapsed = (now - new Date(scan.startedAt).getTime()) / 1000;
        if (elapsed <= 0) {
            etaCacheRef.current[scan.scanId] = {eta: null, computedAt: now};
            return null;
        }

        const rate = current / elapsed; // items per second
        const remaining = total - current;
        const secondsLeft = Math.round(remaining / rate);
        if (secondsLeft < 0) {
            etaCacheRef.current[scan.scanId] = {eta: null, computedAt: now};
            return null;
        }

        const mins = Math.floor(secondsLeft / 60);
        const secs = secondsLeft % 60;
        const eta = `${mins}:${secs.toString().padStart(2, "0")} min left`;
        etaCacheRef.current[scan.scanId] = {eta, computedAt: now};
        return eta;
    }

    return (
        <Popover>
            <Button isIconOnly variant="tertiary">
                {scanInProgress ?
                    <Spinner size="sm" color="current"/> :
                    <TargetIcon className="fill-muted"/>
                }
            </Button>
            <Popover.Content placement="bottom end">
                <Popover.Dialog>
                    <Popover.Arrow/>
                    <div className="flex flex-col gap-2 m-2 min-w-md">
                    {scans.length === 0 ?
                        <p className="flex h-12 items-center justify-center text-sm text-muted">
                            No scans in progress or in history.
                        </p> :
                        <ScrollShadow hideScrollBar className="max-h-96">
                            {scans.map((scan, index) =>
                                <div className="flex flex-col" key={scan.scanId}>
                                    <div
                                        className="flex flex-row gap-4 justify-between items-center text-muted mb-1">
                                        <p>{toTitleCase(scan.type)} scan for library&nbsp;
                                            <Link className="underline text-sm"
                                                  href={`/administration/games/library/${scan.libraryId}`}>
                                                {libraries[scan.libraryId].name}
                                            </Link>
                                        </p>
                                        {scan.finishedAt ?
                                            <p className="text-muted">
                                                Finished {timeUntil(scan.finishedAt)}
                                            </p> :
                                            <p className="text-muted">
                                                Started {timeUntil(scan.startedAt)}
                                            </p>
                                        }
                                    </div>
                                    {scan.status === LibraryScanStatus.IN_PROGRESS &&
                                        (scan.currentStep.current && scan.currentStep.total ?
                                                <div className="flex flex-col gap-1">
                                                    <div className="flex flex-row justify-between">
                                                        <p className="text-muted">
                                                            {`${scan.currentStep.description} (${scan.currentStep.current}/${scan.currentStep.total})`}
                                                        </p>
                                                        <p className="text-muted">
                                                            {estimateTimeLeft(scan)}
                                                        </p>
                                                    </div>
                                                    <ProgressBar
                                                        value={scan.currentStep.current / scan.currentStep.total * 100}
                                                        size="sm">
                                                        <ProgressBar.Track>
                                                            <ProgressBar.Fill/>
                                                        </ProgressBar.Track>
                                                    </ProgressBar>
                                                </div> :
                                                <div className="flex flex-col gap-1">
                                                    <p className="text-muted">{scan.currentStep.description}</p>
                                                    <ProgressBar isIndeterminate size="sm">
                                                        <ProgressBar.Track>
                                                            <ProgressBar.Fill/>
                                                        </ProgressBar.Track>
                                                    </ProgressBar>
                                                </div>
                                        )
                                    }
                                    {scan.status === LibraryScanStatus.COMPLETED &&
                                        <p>
                                            {scan.result?.new} new /&nbsp;
                                            {(scan as any).result?.updated != null && `${(scan as any).result.updated} updated / `}
                                            {scan.result?.removed} removed /&nbsp;
                                            {scan.result?.unmatched} unmatched&nbsp;
                                            (in {timeBetween(scan.startedAt, scan.finishedAt!)})
                                        </p>
                                    }
                                    {scan.status === LibraryScanStatus.FAILED &&
                                        <p className="text-danger flex flex-row gap-1"><WarningIcon weight="fill"/>
                                            Scan failed (check logs for details)
                                        </p>
                                    }
                                    {scans.length > 1 && index < (scans.length - 1) && <Separator className="my-2"/>}
                                </div>
                            )}
                        </ScrollShadow>
                    }
                    </div>
                </Popover.Dialog>
            </Popover.Content>
        </Popover>
    );
}