import {useEffect, useState} from "react";
import {GameEndpoint} from "Frontend/generated/endpoints";
import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import {randomGamesFromLibrary} from "Frontend/util/utils";
import {CoverRow} from "Frontend/components/general/CoverRow";
import {useSnapshot} from "valtio/react";
import {libraryState} from "Frontend/state/LibraryState";

export default function HomeView() {
    const [recentlyAddedGames, setRecentlyAddedGames] = useState<GameDto[]>([]);
    const [libraryIdToGames, setLibraryIdToGames] = useState<Map<number, GameDto[]>>(new Map());
    const state = useSnapshot(libraryState);

    useEffect(() => {
        const gamePromises = state.libraries.map((library) =>
            //@ts-ignore
            randomGamesFromLibrary(library).then((games) => [library.id, games] as [number, GameDto[]])
        );

        Promise.all(gamePromises).then((results) => {
            const libraryGamesMap = new Map<number, GameDto[]>();
            results.forEach(([libraryId, games]) => {
                libraryGamesMap.set(libraryId, games);
            });
            setLibraryIdToGames(libraryGamesMap);
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
                {state.libraries.map((library) => (
                    <CoverRow key={library.id} title={library.name}
                              games={libraryIdToGames.get(library.id) || []}
                              onPressShowMore={() => alert(`show more of library '${library.name}'`)}
                    />
                ))}
            </div>
        </div>
    );
}