import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {CoverRow} from "Frontend/components/general/covers/CoverRow";
import {useSnapshot} from "valtio/react";
import {libraryState} from "Frontend/state/LibraryState";
import {gameState} from "Frontend/state/GameState";
import React, {useEffect, useState} from "react";
import LibraryDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryDto";
import {ConfigEndpoint} from "Frontend/generated/endpoints";
import {collectionState} from "Frontend/state/CollectionState";
import CollectionDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionDto";
import {StartPageDisplayCard} from "Frontend/components/general/cards/StartPageDisplayCard";

export default function HomeView() {
    const librariesState = useSnapshot(libraryState);
    const collectionsState = useSnapshot(collectionState);
    const gamesState = useSnapshot(gameState);
    const recentlyAddedGames = gamesState.recentlyAdded as GameDto[];
    const gamesByLibrary = gamesState.gamesByLibraryId as Record<number, GameDto[]>;
    const gamesByCollection = gamesState.gamesByCollectionId as Record<number, GameDto[]>;

    const [showRecentlyAdded, setShowRecentlyAdded] = useState<boolean>(false);
    const [filteredAndSortedLibraries, setFilteredAndSortedLibraries] = useState<LibraryDto[]>([]);
    const [filteredAndSortedCollections, setFilteredAndSortedCollections] = useState<CollectionDto[]>([]);

    useEffect(() => {
        ConfigEndpoint.showRecentlyAddedOnHomepage().then(setShowRecentlyAdded);
    }, []);

    useEffect(() => {
        const libraries = librariesState.sorted
            .filter(library => library.metadata!.displayOnHomepage)
            .filter(library =>
                gamesByLibrary[library.id] && gamesByLibrary[library.id].length > 0
            );

        setFilteredAndSortedLibraries(libraries as LibraryDto[]);

        const collections = collectionsState.sorted
            .filter(collection => collection.metadata!.displayOnHomepage)
            .filter(collection =>
                gamesByCollection[collection.id] && gamesByCollection[collection.id].length > 0
            );

        setFilteredAndSortedCollections(collections as CollectionDto[]);

    }, [librariesState.sorted, collectionsState.sorted, gamesByLibrary, gamesByCollection]);

    return (
        <div className="w-full">
            <div className="flex flex-col gap-4">
                {(filteredAndSortedLibraries.length === 0 && filteredAndSortedCollections.length === 0) &&
                    <div className="flex flex-col gap-2">
                        <p className="text-2xl font-bold mb-4">All games</p>
                        <div className="flex flex-row gap-4">
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
                {recentlyAddedGames.length > 0 && showRecentlyAdded &&
                    <CoverRow title="Recently added" games={recentlyAddedGames} link="/recently-added"/>
                }
                {filteredAndSortedLibraries.map((library) => (
                    <CoverRow key={library.id} title={library.name}
                              games={gamesByLibrary[library.id] || []}
                              link={"/library/" + library.id}
                    />
                ))}
                {filteredAndSortedCollections.map((collection) => (
                    <CoverRow key={collection.id} title={collection.name}
                              games={gamesByCollection[collection.id] || []}
                              link={"/collection/" + collection.id}
                    />
                ))}
            </div>
        </div>
    );
}