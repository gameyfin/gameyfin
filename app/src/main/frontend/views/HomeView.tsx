import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {CoverRow} from "Frontend/components/general/covers/CoverRow";
import {useSnapshot} from "valtio/react";
import {libraryState} from "Frontend/state/LibraryState";
import {gameState} from "Frontend/state/GameState";
import {useNavigate} from "react-router";
import {useEffect, useState} from "react";
import LibraryDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryDto";
import {ConfigEndpoint} from "Frontend/generated/endpoints";

export default function HomeView() {
    const navigate = useNavigate();
    const librariesState = useSnapshot(libraryState);
    const gamesState = useSnapshot(gameState);
    const recentlyAddedGames = gamesState.recentlyAdded as GameDto[];
    const gamesByLibrary = gamesState.gamesByLibraryId as Record<number, GameDto[]>;

    const [showRecentlyAdded, setShowRecentlyAdded] = useState<boolean>(false);
    const [filteredAndSortedLibraries, setFilteredAndSortedLibraries] = useState<LibraryDto[]>([]);

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
    }, [librariesState.sorted, gamesByLibrary]);

    return (
        <div className="w-full">
            <div className="flex flex-col gap-2">
                {recentlyAddedGames.length > 0 && showRecentlyAdded &&
                    <CoverRow title="Recently added" games={recentlyAddedGames}
                              onPressShowMore={() => navigate("/recently-added")}/>
                }
                {filteredAndSortedLibraries.map((library) => (
                    <CoverRow key={library.id} title={library.name}
                              games={gamesByLibrary[library.id] || []}
                              onPressShowMore={() => navigate("/library/" + library.id)}
                    />
                ))}
            </div>
        </div>
    );
}