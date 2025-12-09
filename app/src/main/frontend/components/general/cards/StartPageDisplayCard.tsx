import {Card, Image} from "@heroui/react";
import React, {useMemo} from "react";
import LibraryDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryDto";
import CollectionDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionDto";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import Rand from "rand-seed";


interface StartPageDisplayCardProps {
    item: LibraryDto | CollectionDto;
}

export function StartPageDisplayCard({item}: StartPageDisplayCardProps) {
    const isCollection = (libraryOrCollection: LibraryDto | CollectionDto): libraryOrCollection is CollectionDto => {
        return 'description' in libraryOrCollection;
    };

    const isLibrary = (libraryOrCollection: LibraryDto | CollectionDto): libraryOrCollection is LibraryDto => {
        return !('description' in libraryOrCollection);
    };

    const gamesState = useSnapshot(gameState);
    const randomImageId = useMemo<number | null>(() => getRandomImageId(), [item]);

    /**
     * Gets a random cover ID from the games in the specified library or collection.
     * Since the Random class is seeded with the game ID, the same game and image will always be selected for a given library/collection (unless the games inside change).
     * @return {number | null} The random cover ID or null if none found.
     */
    function getRandomImageId(): number | null {
        let game: GameDto | null = null;

        if (isCollection(item)) {
            game = gamesState.randomlyOrderedGamesByCollectionId[item.id][0] as GameDto;
        } else if (isLibrary(item)) {
            game = gamesState.randomlyOrderedGamesByLibraryId[item.id][0] as GameDto;
        }

        if (!game) return null;

        const random = new Rand(game.id.toString());
        return game.imageIds![Math.floor(random.next() * game.imageIds!.length)];
    }

    return randomImageId && (
        <Card
            className="h-48 w-96 relative overflow-hidden scale-95 hover:scale-100 shine transition-all cursor-pointer select-none">
            <Image
                src={`images/cover/${randomImageId}`}
                className="absolute inset-0 w-full h-full object-cover brightness-40 z-0"
                removeWrapper
            />
            <div className="relative z-10 flex items-center justify-center h-full">
                <h2 className="text-white text-2xl font-bold text-center px-4">
                    {item.name}
                </h2>
            </div>
        </Card>
    );
}