import {
    Button,
    Divider,
    Link,
    Popover,
    PopoverContent,
    PopoverTrigger,
    Progress,
    ScrollShadow,
    Spinner
} from "@heroui/react";
import {useSnapshot} from "valtio/react";
import {scanState} from "Frontend/state/ScanState";
import LibraryScanProgress from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryScanProgress";
import {libraryState} from "Frontend/state/LibraryState";
import {Target, Warning} from "@phosphor-icons/react";
import {timeBetween, timeUntil, toTitleCase} from "Frontend/util/utils";
import LibraryScanStatus from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryScanStatus";
import {useEffect, useState} from "react";

export default function ScanProgressPopover() {
    const libraries = useSnapshot(libraryState).state;
    const scans = useSnapshot(scanState).sortedByStartTime as LibraryScanProgress[];
    const scanInProgress = useSnapshot(scanState).isScanning;

    // Add state to track current time and force re-renders
    const [currentTime, setCurrentTime] = useState(Date.now());

    // Set up an interval to update the time every second
    useEffect(() => {
        const intervalId = setInterval(() => {
            setCurrentTime(Date.now());
        }, 1000);

        // Clean up the interval when component unmounts
        return () => clearInterval(intervalId);
    }, []);

    return (
        <Popover placement="bottom-end" showArrow={true}>
            <PopoverTrigger>
                <Button isIconOnly variant="light">
                    {scanInProgress ?
                        <Spinner size="sm" color="default" variant="spinner"
                                 classNames={{
                                     spinnerBars: "bg-foreground-500",
                                 }}/> :
                        <Target className="fill-foreground-500"/>
                    }
                </Button>
            </PopoverTrigger>
            <PopoverContent>
                <div className="flex flex-col gap-2 m-2 min-w-96 w-fit">
                    {scans.length === 0 ?
                        <p className="flex h-12 items-center justify-center text-sm text-default-500">
                            No scans in progress or in history.
                        </p> :
                        <ScrollShadow hideScrollBar className="max-h-96">
                            {scans.map((scan, index) =>
                                <div className="flex flex-col">
                                    <div
                                        className="flex flex-row justify-between items-center text-default-500 mb-1">
                                        <p>{toTitleCase(scan.type)} scan for library&nbsp;
                                            <Link underline="always"
                                                  color="foreground"
                                                  size="sm"
                                                  href={`/administration/libraries/library/${scan.libraryId}`}>
                                                {libraries[scan.libraryId].name}
                                            </Link>
                                        </p>
                                        {scan.finishedAt ?
                                            <p className="text-default-500">
                                                Finished {timeUntil(scan.finishedAt)}
                                            </p> :
                                            <p className="text-default-500">
                                                Started {timeUntil(scan.startedAt)}
                                            </p>
                                        }
                                    </div>
                                    {scan.status === LibraryScanStatus.IN_PROGRESS &&
                                        (scan.currentStep.current && scan.currentStep.total ?
                                                <div className="flex flex-col gap-1">
                                                    <p className="text-default-500">
                                                        {`${scan.currentStep.description} (${scan.currentStep.current}/${scan.currentStep.total})`}
                                                    </p>
                                                    <Progress
                                                        value={scan.currentStep.current / scan.currentStep.total * 100}
                                                        size="sm"/>
                                                </div> :
                                                <div className="flex flex-col gap-1">
                                                    <p className="text-default-500">{scan.currentStep.description}</p>
                                                    <Progress isIndeterminate size="sm"/>
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
                                        <p className="text-danger flex flex-row gap-1"><Warning weight="fill"/>
                                            Scan failed (check logs for details)
                                        </p>
                                    }
                                    {scans.length > 1 && index < (scans.length - 1) && <Divider className="my-2"/>}
                                </div>
                            )}
                        </ScrollShadow>
                    }
                </div>
            </PopoverContent>
        </Popover>
    );
}