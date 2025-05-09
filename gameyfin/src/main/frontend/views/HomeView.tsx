import {useEffect, useState} from "react";
import {GameEndpoint, LibraryEndpoint} from "Frontend/generated/endpoints";
import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryDto";
import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import {randomGamesFromLibrary} from "Frontend/util/utils";
import {CoverRow} from "Frontend/components/general/CoverRow";

export default function HomeView() {
    const [recentlyAddedGames, setRecentlyAddedGames] = useState<GameDto[]>([]);
    const [libraries, setLibraries] = useState<LibraryDto[]>([]);
    const [libraryIdToGames, setLibraryIdToGames] = useState<Map<number, GameDto[]>>(new Map());

    useEffect(() => {
        LibraryEndpoint.getAllLibraries().then(libraries => {
            setLibraries(libraries);

            const gamePromises = libraries.map((library) =>
                randomGamesFromLibrary(library).then((games) => [library.id, games] as [number, GameDto[]])
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
            <div className="flex flex-col gap-2">
                <CoverRow title="Recently added" games={recentlyAddedGames}
                          onPressShowMore={() => alert("show more of 'Recently added'")}/>
                {libraries.map((library) => (
                    <CoverRow key={library.id} title={library.name}
                              games={libraryIdToGames.get(library.id) || []}
                              onPressShowMore={() => alert(`show more of library '${library.name}'`)}
                    />
                ))}
            </div>
        </div>
    );
}