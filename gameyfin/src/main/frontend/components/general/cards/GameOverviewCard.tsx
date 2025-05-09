import {GameCover} from "Frontend/components/general/covers/GameCover";
import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";

export function GameOverviewCard({game}: { game: GameDto }) {
    return (
        <GameCover game={game} radius="sm"></GameCover>
    );
}