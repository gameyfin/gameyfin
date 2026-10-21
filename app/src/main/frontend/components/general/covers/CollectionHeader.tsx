import CollectionDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionDto";
import React, {useMemo} from "react";
import {useRandomlyOrderedGamesByCollectionId} from "Frontend/state/derived";
import IconBackgroundPattern from "Frontend/components/general/IconBackgroundPattern";
import {Card} from "@heroui/react";

interface CollectionHeaderProps {
    collection: CollectionDto;
    className?: string;
}

export default function CollectionHeader({collection, className}: CollectionHeaderProps) {
    const MAX_COVER_COUNT = 5;
    const randomlyOrderedGames = useRandomlyOrderedGamesByCollectionId()[collection.id];
    const randomGames = useMemo(() => (randomlyOrderedGames ?? [])
        .filter(game => game.previewImage)
        .slice(0, MAX_COVER_COUNT), [randomlyOrderedGames]);

    return (
        <Card className={`overflow-hidden rounded-lg relative pointer-events-none select-none ${className}`}>
            <IconBackgroundPattern/>
            <div className="flex flex-row items-center w-full h-full brightness-50">
                {randomGames.map((game, idx) => (
                    <div
                        key={idx}
                        className="flex-none overflow-hidden -ml-[10%]"
                        style={{
                            width: `calc(100% / ${MAX_COVER_COUNT - 2})`,
                            clipPath: 'polygon(15% 0, 100% 0, 85% 100%, 0% 100%)',
                        }}
                    >
                        <img
                            src={`/images/screenshot/${game.previewImage!.id}`}
                            alt={`Image ${idx}`}
                        />
                    </div>
                ))}
            </div>
            <div className="absolute inset-0 flex items-center justify-center">
                <h2 className="text-white text-3xl font-bold">{collection.name}</h2>
            </div>
        </Card>
    );
}