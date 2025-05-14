import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import {Image} from "@heroui/react";
import {GameCoverFallback} from "Frontend/components/general/covers/GameCoverFallback";

interface GameCoverProps {
    game: GameDto;
    size?: number;
    radius?: "none" | "sm" | "md" | "lg";
    hover?: boolean;
}

export function GameCover({game, size = 300, radius = "sm", hover = false}: GameCoverProps) {
    return (
        Number.isInteger(game.coverId) ? (
            <Image
                alt={game.title}
                className={`z-0 w-full h-full object-cover aspect-[12/17] ${hover ? "scale-95 hover:scale-100" : ""}`}
                src={`images/cover/${game.coverId}`}
                radius={radius}
                height={size}
                fallbackSrc={<GameCoverFallback title={game.title} size={size} radius={radius}/>}
            />
        ) : <GameCoverFallback title={game.title} size={size} radius={radius} hover={hover}/>
    );
}