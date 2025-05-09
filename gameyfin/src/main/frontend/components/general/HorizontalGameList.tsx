import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import Section from "Frontend/components/general/Section";
import {GameCover} from "Frontend/components/general/GameCover";
import {Card} from "@heroui/react";

interface HorizontalGameListProps {
    title: string;
    games: GameDto[];
}

export function HorizontalGameList({title, games}: HorizontalGameListProps) {
    return (
        <div className="flex flex-col gap-2">
            <Section title={title}/>
            <div className="flex flex-row gap-4 overflow-x-auto">
                {games.length > 0 ?
                    games.map((game) => (
                        <GameCover game={game}/>
                    ))
                    : <Card className="h-[300px] aspect-[12/17]">
                        <div className="flex flex-col items-center justify-center h-full">
                            <p className="text-gray-500">No content</p>
                        </div>
                    </Card>
                }
            </div>
        </div>
    );
}