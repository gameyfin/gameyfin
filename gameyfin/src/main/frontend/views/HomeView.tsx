import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import {CoverRow} from "Frontend/components/general/covers/CoverRow";
import {useSnapshot} from "valtio/react";
import {libraryState} from "Frontend/state/LibraryState";
import {gameState} from "Frontend/state/GameState";
import {useNavigate} from "react-router";

export default function HomeView() {
    const navigate = useNavigate();
    const librariesState = useSnapshot(libraryState);
    const gamesState = useSnapshot(gameState);
    const recentlyAddedGames = gamesState.recentlyAdded as GameDto[];
    const gamesByLibrary = gamesState.gamesByLibraryId as Record<number, GameDto[]>;

    return (
        <div className="w-full">
            <div className="flex flex-col gap-2">
                <CoverRow title="Recently added" games={recentlyAddedGames}
                          onPressShowMore={() => navigate("/recently-added")}/>
                {librariesState.libraries.map((library) => (
                    <CoverRow key={library.id} title={library.name}
                              games={gamesByLibrary[library.id] || []}
                              onPressShowMore={() => navigate("/library/" + library.id)}
                    />
                ))}
            </div>
        </div>
    );
}