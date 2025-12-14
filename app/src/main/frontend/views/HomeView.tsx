import {CoverRow} from "Frontend/components/general/covers/CoverRow";
import {useSnapshot} from "valtio/react";
import {libraryState} from "Frontend/state/LibraryState";
import {gameState} from "Frontend/state/GameState";
import React, {useEffect, useState} from "react";
import LibraryDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryDto";
import {collectionState} from "Frontend/state/CollectionState";
import CollectionDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionDto";
import {StartPageDisplayCard} from "Frontend/components/general/cards/StartPageDisplayCard";
import {Link} from "@heroui/react";
import {CaretRightIcon} from "@phosphor-icons/react";

export default function HomeView() {
    const librariesState = useSnapshot(libraryState);
    const collectionsState = useSnapshot(collectionState);
    const gamesState = useSnapshot(gameState);
    const gamesByLibrary = gamesState.gamesByLibraryId;
    const gamesByCollection = gamesState.gamesByCollectionId;

    const [filteredAndSortedLibraries, setFilteredAndSortedLibraries] = useState<LibraryDto[]>([]);
    const [filteredAndSortedCollections, setFilteredAndSortedCollections] = useState<CollectionDto[]>([]);

    useEffect(() => {
        const libraries = librariesState.sorted
            .filter(library => library.metadata!.displayOnHomepage)
            .filter(library =>
                gamesByLibrary[library.id] && gamesByLibrary[library.id].length > 0
            );

        setFilteredAndSortedLibraries(libraries);

        const collections = collectionsState.sorted
            .filter(collection => collection.metadata!.displayOnHomepage)
            .filter(collection =>
                gamesByCollection[collection.id] && gamesByCollection[collection.id].length > 0
            );

        setFilteredAndSortedCollections(collections);

    }, [librariesState.sorted, collectionsState.sorted, gamesByLibrary, gamesByCollection]);

    // Sort games by date added (newest first) for libraries
    const getSortedLibraryGames = (libraryId: number) => {
        const games = gamesByLibrary[libraryId] || [];
        return [...games].sort((a, b) => {
            const dateA = new Date(a.createdAt).getTime();
            const dateB = new Date(b.createdAt).getTime();
            return dateB - dateA; // Descending order (newest first)
        });
    };

    // Sort games by date added (newest first) for collections
    const getSortedCollectionGames = (collection: CollectionDto) => {
        const games = gamesByCollection[collection.id] || [];
        const gamesAddedAt = collection.metadata?.gamesAddedAt || {};

        return [...games].sort((a, b) => {
            const dateA = gamesAddedAt[a.id.toString()]
                ? new Date(gamesAddedAt[a.id.toString()]).getTime()
                : 0;
            const dateB = gamesAddedAt[b.id.toString()]
                ? new Date(gamesAddedAt[b.id.toString()]).getTime()
                : 0;
            return dateB - dateA; // Descending order (newest first)
        });
    };

    return (
        <div className="w-full">
            <div className="flex flex-col gap-4">
                {(filteredAndSortedLibraries.length + filteredAndSortedCollections.length > 0) &&
                    <div className="flex flex-col gap-2">
                        <Link href="/search" className="flex flex-row gap-1 w-fit items-baseline" color="foreground"
                              underline="hover">
                            <p className="text-2xl font-bold mb-4">Your games</p>
                            <CaretRightIcon weight="bold" size={16}/>
                        </Link>
                        <div className="grid gap-4 grid-cols-[repeat(auto-fill,minmax(353px,1fr))]">
                            {filteredAndSortedLibraries.length > 0 &&
                                filteredAndSortedLibraries.map((library: LibraryDto) => (
                                    <StartPageDisplayCard key={library.id} item={library}/>
                                ))
                            }
                            {filteredAndSortedCollections.length > 0 &&
                                filteredAndSortedCollections.map((collection: CollectionDto) => (
                                    <StartPageDisplayCard key={collection.id} item={collection}/>
                                ))
                            }
                        </div>
                    </div>
                }
                {filteredAndSortedLibraries.map((library) => (
                    <CoverRow key={library.id} title={library.name}
                              games={getSortedLibraryGames(library.id)}
                              link={"/library/" + library.id}
                    />
                ))}
                {filteredAndSortedCollections.map((collection) => (
                    <CoverRow key={collection.id} title={collection.name}
                              games={getSortedCollectionGames(collection)}
                              link={"/collection/" + collection.id}
                    />
                ))}
            </div>
        </div>
    );
}