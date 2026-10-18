import {Card, Chip} from "@heroui/react";
import React, {useMemo} from "react";
import LibraryDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryDto";
import CollectionDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionDto";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import Rand from "rand-seed";
import {useNavigate} from "react-router";
import ImageDto from "Frontend/generated/org/gameyfin/app/media/ImageDto";
import BlurhashImage from "Frontend/components/general/covers/BlurhashImage";


interface StartPageDisplayCardProps {
    item: LibraryDto | CollectionDto;
}

export function StartPageDisplayCard({item}: StartPageDisplayCardProps) {
    const navigate = useNavigate();

    const isCollection = (libraryOrCollection: LibraryDto | CollectionDto): libraryOrCollection is CollectionDto => {
        return 'description' in libraryOrCollection;
    };

    const isLibrary = (libraryOrCollection: LibraryDto | CollectionDto): libraryOrCollection is LibraryDto => {
        return !('description' in libraryOrCollection);
    };

    const gamesState = useSnapshot(gameState);
    const randomImage = useMemo<ImageDto | null>(() => getRandomImage(), [item]);
    const link = useMemo<string>(() => getLink(), [item]);
    const type = isCollection(item) ? 'Collection' : 'Library';

    /**
     * Gets a random screenshot from the games in the specified library or collection.
     * Since the Random class is seeded with the game ID, the same game and image will always be selected for a given library/collection (unless the games inside change).
     * @return {ImageDto | null} The random screenshot or null if none found.
     */
    function getRandomImage(): ImageDto | null {
        let games: GameDto[] = [];

        if (isCollection(item)) {
            games = gamesState.randomlyOrderedGamesByCollectionId[item.id] as GameDto[];
        } else if (isLibrary(item)) {
            games = gamesState.randomlyOrderedGamesByLibraryId[item.id] as GameDto[];
        }

        if (!games || games.length == 0) return null;

        // Find the first game that has at least one screenshot available
        let game: GameDto | undefined = games.find(game => game.images && game.images.length > 0);

        if (!game) return null;

        const random = new Rand(`${item.id}-${game.id}`);
        const randomImageIndex = Math.floor(random.next() * game.images!.length);
        return game.images![randomImageIndex] as ImageDto;
    }

    function getLink(): string {
        if (isCollection(item)) {
            return `/collection/${item.id}`;
        } else if (isLibrary(item)) {
            return `/library/${item.id}`;
        }
        return '#';
    }

    return randomImage && (
        <Card className="h-48 w-96 relative overflow-hidden p-0 scale-95 hover:scale-100 shine transition-all select-none">
            <button
                type="button"
                onClick={() => navigate(link)}
                className="relative h-full w-full cursor-pointer text-left"
                aria-label={`Open ${type.toLowerCase()} ${item.name}`}
            >
                <BlurhashImage
                    src={`images/cover/${randomImage.id}`}
                    blurhash={randomImage.blurhash}
                    placeholderSize={{width: 32, height: 16}}
                    alt={`${item.name} background`}
                    className="absolute inset-0 h-full w-full object-cover brightness-40 z-0"
                />
                <div className="flex flex-col gap-1 relative z-10 items-center justify-center h-full">
                    <h2 className="text-white text-2xl font-bold text-center px-4">
                        {item.name}
                    </h2>
                    <Chip size="sm" className="rounded-sm">{type}</Chip>
                </div>
            </button>
        </Card>
    );
}