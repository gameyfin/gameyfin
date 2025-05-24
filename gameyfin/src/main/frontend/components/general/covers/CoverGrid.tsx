import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import {GameCover} from "Frontend/components/general/covers/GameCover";

interface CoverGridProps {
    games: GameDto[];
}

export default function CoverGrid({games}: CoverGridProps) {
    return (
        <div className="grid grid-cols-[repeat(auto-fill,minmax(180px,212px))] gap-4 justify-center">
            {games.map((game) => (
                <GameCover key={game.id} game={game} interactive={true}/>
            ))}
        </div>
    );
}