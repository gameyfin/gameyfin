import React from "react";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import {useSnapshot} from "valtio/react";
import {libraryState} from "Frontend/state/LibraryState";
import LibrarySummaryDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibrarySummaryDto";
import LibraryUpdateDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryUpdateDto";
import PrioritiesModal from "./PrioritiesModal";

interface LibraryPrioritiesModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
}

export default function LibraryPrioritiesModal({isOpen, onOpenChange}: LibraryPrioritiesModalProps) {

    const libraries = useSnapshot(libraryState).sorted;

    const updateLibraries = async (reorderedLibraries: LibrarySummaryDto[]) => {
        const updateDtos: LibraryUpdateDto[] = reorderedLibraries.map((library, index): LibraryUpdateDto => {
            return {
                id: library.id,
                metadata: {
                    displayOnHomepage: library.metadata!.displayOnHomepage,
                    displayOrder: index
                }
            };
        });
        await LibraryEndpoint.updateLibraries(updateDtos);
    };

    return (
        <PrioritiesModal
            title="Edit library order"
            subtitle="Libraries higher on the list are displayed at the start"
            items={libraries}
            updateItems={updateLibraries}
            isOpen={isOpen}
            onOpenChange={onOpenChange}
        />
    );
}