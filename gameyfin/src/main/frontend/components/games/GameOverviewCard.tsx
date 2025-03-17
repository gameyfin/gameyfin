import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import {Card, Image} from "@heroui/react";

export function GameOverviewCard({game}: { game: GameDto }) {
    return (
        <Card className="h-80 aspect-[12/17]">
            <Image
                removeWrapper
                alt={game.title}
                className="z-0 w-full h-full object-cover"
                src={`images/cover/${game.coverId}`}
            />
        </Card>
    );
}