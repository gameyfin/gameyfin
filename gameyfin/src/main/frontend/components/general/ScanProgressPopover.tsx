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
import {clear, scanState} from "Frontend/state/ScanState";
import LibraryScanProgress from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryScanProgress";
import {libraryState} from "Frontend/state/LibraryState";
import {Target} from "@phosphor-icons/react";
import {timeUntil} from "Frontend/util/utils";
import LibraryScanStatus from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryScanStatus";

export default function ScanProgressPopover() {
    const libraries = useSnapshot(libraryState).state;
    const scans = useSnapshot(scanState).sortedByStartTime as LibraryScanProgress[];
    const scanInProgress = useSnapshot(scanState).isScanning;

    return (
        <Popover placement="bottom-end" showArrow={true}>
            <PopoverTrigger>
                <Button isIconOnly variant="light">
                    {scanInProgress ?
                        <Spinner size="sm" color="default" variant="simple"/> :
                        <Target/>
                    }
                </Button>
            </PopoverTrigger>
            <PopoverContent>
                <div className="flex flex-col gap-2 m-2 w-96">
                    {scans.length === 0 ?
                        <p className="flex h-12 items-center justify-center text-sm text-default-500">
                            No scans in progress.
                        </p> :
                        <div className="flex flex-col gap-4">
                            <Link underline="always" size="sm" href="#" onPress={clear} className="justify-end">
                                Clear
                            </Link>
                            <ScrollShadow hideScrollBar className="max-h-96">
                                {scans.map((scan, index) =>
                                    <div className="flex flex-col">
                                        <div
                                            className="flex flex-row justify-between items-center text-default-500 mb-1">
                                            <p>Scan for library&nbsp;
                                                <Link underline="always"
                                                      color="foreground"
                                                      size="sm"
                                                      href={`/administration/libraries/library/${scan.libraryId}`}>
                                                    {libraries[scan.libraryId].name}
                                                </Link>
                                            </p>
                                            {scan.finishedAt ?
                                                <p className="text-default-500">Finished {timeUntil(scan.finishedAt)}</p> :
                                                <p className="text-default-500">Started {timeUntil(scan.startedAt)}</p>
                                            }
                                        </div>
                                        {scan.status === LibraryScanStatus.IN_PROGRESS ?
                                            scan.currentStep.current && scan.currentStep.total ?
                                                <div>
                                                    <p className="text-default-500">
                                                        {`${scan.currentStep.description} (${scan.currentStep.current} / ${scan.currentStep.total})`}
                                                    </p>
                                                    <Progress
                                                        value={scan.currentStep.current / scan.currentStep.total * 100}
                                                        size="sm"/>
                                                </div> :
                                                <div>
                                                    <p className="text-default-500">{scan.currentStep.description}</p>
                                                    <Progress isIndeterminate size="sm"/>
                                                </div>
                                            :
                                            <p>
                                                {scan.result?.new} new /&nbsp;
                                                {scan.result?.removed} removed /&nbsp;
                                                {scan.result?.unmatched} unmatched
                                            </p>
                                        }
                                        {scans.length > 1 && index < (scans.length - 1) && <Divider className="my-2"/>}
                                    </div>
                                )}
                            </ScrollShadow>
                        </div>
                    }
                </div>
            </PopoverContent>
        </Popover>
    );
}