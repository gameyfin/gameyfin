import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import {Image} from "@heroui/react";

interface GameCoverProps {
    game: GameDto;
    size?: number;
    radius?: "none" | "sm" | "md" | "lg" | "full";
}

export function GameCover({game, size = 300, radius}: GameCoverProps) {
    return (
        <Image
            alt={game.title}
            className="z-0 w-full h-full object-cover aspect-[12/17]"
            src={`images/cover/${game.coverId}`}
            radius={radius}
            height={size}
        />
    );
}