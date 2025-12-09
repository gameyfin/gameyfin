import React from "react";
import {CollectionEndpoint} from "Frontend/generated/endpoints";
import {useSnapshot} from "valtio/react";
import {collectionState} from "Frontend/state/CollectionState";
import CollectionDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionDto";
import CollectionUpdateDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionUpdateDto";
import PrioritiesModal from "./PrioritiesModal";

interface CollectionPrioritiesModalProps {
    isOpen: boolean;
    onOpenChange: () => void;
}

export default function CollectionPrioritiesModal({isOpen, onOpenChange}: CollectionPrioritiesModalProps) {

    const collections = useSnapshot(collectionState).sorted;

    const updateCollections = async (reorderedCollections: any[]) => {
        const updateDtos: CollectionUpdateDto[] = reorderedCollections.map((collection, index): CollectionUpdateDto => {
            return {
                id: collection.id,
                metadata: {
                    displayOnHomepage: collection.metadata!.displayOnHomepage,
                    displayOrder: index
                }
            };
        });
        await CollectionEndpoint.updateCollections(updateDtos);
    };

    return (
        <PrioritiesModal
            title="Edit collection order"
            subtitle="Collections higher on the list are displayed at the start"
            items={collections as CollectionDto[]}
            updateItems={updateCollections}
            isOpen={isOpen}
            onOpenChange={onOpenChange}
        />
    );
}

