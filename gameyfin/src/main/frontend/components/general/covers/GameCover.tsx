import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import {Image} from "@heroui/react";
import {GameCoverFallback} from "Frontend/components/general/covers/GameCoverFallback";

interface GameCoverProps {
    game: GameDto;
    size?: number;
    radius?: "none" | "sm" | "md" | "lg";
    interactive?: boolean;
}

export function GameCover({game, size = 300, radius = "sm", interactive = false}: GameCoverProps) {
    const coverContent = Number.isInteger(game.coverId) ? (
        <div className={`${interactive ? "rounded-md scale-95 hover:scale-100 shine transition-all" : ""}`}>
            <Image
                alt={game.title}
                className="z-0 object-cover aspect-[12/17]"
                src={`images/cover/${game.coverId}`}
                radius={radius}
                height={size}
                fallbackSrc={<GameCoverFallback title={game.title} size={size} radius={radius}/>}
            />
        </div>
    ) : (
        <GameCoverFallback title={game.title} size={size} radius={radius} hover={interactive}/>
    );

    return interactive ? (
        <a href={`/game/${game.id}`}>
            {coverContent}
        </a>
    ) : coverContent;
}