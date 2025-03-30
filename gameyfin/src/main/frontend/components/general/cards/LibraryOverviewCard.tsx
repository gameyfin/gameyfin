import {Card} from "@heroui/react";
import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/LibraryDto";

export function LibraryOverviewCard({library}: { library: LibraryDto }) {
    return (
        <Card className="flex flex-row justify-between p-2">
            <div className="flex flex-col flex-1 items-center gap-4">
                <p className="text-2xl font-bold">{library.name}</p>
                <p>{library.path}</p>
            </div>
        </Card>
    );
}