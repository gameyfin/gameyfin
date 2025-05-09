import {useEffect, useState} from "react";
import {GameEndpoint, LibraryEndpoint} from "Frontend/generated/endpoints";
import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryDto";
import {HorizontalGameList} from "Frontend/components/general/HorizontalGameList";
import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import {randomGamesFromLibrary} from "Frontend/util/utils";

export default function HomeView() {
    const [recentlyAddedGames, setRecentlyAddedGames] = useState<GameDto[]>([]);
    const [libraries, setLibraries] = useState<LibraryDto[]>([]);
    const [libraryIdToGames, setLibraryIdToGames] = useState<Map<number, GameDto[]>>(new Map());

    useEffect(() => {
        LibraryEndpoint.getAllLibraries().then(libraries => {
            setLibraries(libraries);

            const gamePromises = libraries.map((library) =>
                randomGamesFromLibrary(library, 10).then((games) => [library.id, games] as [number, GameDto[]])
            );

            Promise.all(gamePromises).then((results) => {
                const libraryGamesMap = new Map<number, GameDto[]>();
                results.forEach(([libraryId, games]) => {
                    libraryGamesMap.set(libraryId, games);
                });
                setLibraryIdToGames(libraryGamesMap);
            });
        });

        // TODO: see https://github.com/vaadin/hilla/issues/3470
        GameEndpoint.getMostRecentlyAddedGames(undefined).then(games => {
            setRecentlyAddedGames(games);
        });
    }, []);

    return (
        <div className="w-full">
            <p className="text-center text-2xl font-extrabold">Welcome to Gameyfin!</p>
            <div className="flex flex-col gap-2">
                <HorizontalGameList title="Recently added" games={recentlyAddedGames}/>
                {libraries.map((library) => (
                    <HorizontalGameList key={library.id} title={library.name}
                                        games={libraryIdToGames.get(library.id) || []}/>
                ))}
            </div>
        </div>
    );
}