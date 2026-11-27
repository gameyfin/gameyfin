import {Card, Chip} from "@heroui/react";
import React, {useMemo} from "react";
import LibrarySummaryDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibrarySummaryDto";
import CollectionDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionDto";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import GameSummaryDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSummaryDto";
import {useRandomlyOrderedGamesByCollectionId} from "Frontend/state/derived";
import {useNavigate} from "react-router";
import ImageDto from "Frontend/generated/org/gameyfin/app/media/ImageDto";
import BlurhashImage from "Frontend/components/general/covers/BlurhashImage";


interface StartPageDisplayCardProps {
    item: LibrarySummaryDto | CollectionDto;
}

export function StartPageDisplayCard({item}: StartPageDisplayCardProps) {
    const navigate = useNavigate();

    const isCollection = (libraryOrCollection: LibrarySummaryDto | CollectionDto): libraryOrCollection is CollectionDto => {
        return 'kind' in libraryOrCollection;
    };

    const gamesState = useSnapshot(gameState);
    const randomlyOrderedGamesByCollectionId = useRandomlyOrderedGamesByCollectionId();
    const randomImage = useMemo<ImageDto | null>(() => getRandomImage(), [item, gamesState.state, randomlyOrderedGamesByCollectionId]);
    const link = useMemo<string>(() => getLink(), [item]);
    const type = isCollection(item) ? 'Collection' : 'Library';
    const smart = isCollection(item) && item.smartEnabled;

    /**
     * Gets a screenshot from a random game in the specified library or collection.
     * Since the games are ordered with a seeded random, the same game will always be selected for a given library/collection (unless the games inside change).
     * @return {ImageDto | null} The screenshot or null if none found.
     */
    function getRandomImage(): ImageDto | null {
        let games: readonly GameSummaryDto[] | undefined;

        if (isCollection(item)) {
            games = randomlyOrderedGamesByCollectionId[item.id];
        } else {
            games = gamesState.randomlyOrderedGamesByLibraryId[item.id];
        }

        // Find the first game that has at least one screenshot available
        return games?.find(game => game.previewImage)?.previewImage ?? null;
    }

    function getLink(): string {
        if (isCollection(item)) {
            return `/collection/${item.id}`;
        }
        return `/library/${item.id}`;
    }

    return randomImage && (
        <Card
            className="h-48 w-full max-w-96 relative overflow-hidden p-0 scale-95 hover:scale-100 shine transition-all select-none">
            <button
                type="button"
                onClick={() => navigate(link)}
                className="relative h-full w-full cursor-pointer text-left"
                aria-label={`Open ${type.toLowerCase()} ${item.name}`}
                aria-description={smart ? "Smart collection" : undefined}
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
                    <Chip size="sm" className="rounded-sm">{smart ? "Smart collection" : type}</Chip>
                </div>
            </button>
        </Card>
    );
}
