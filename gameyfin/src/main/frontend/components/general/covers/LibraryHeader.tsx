import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryDto";
import React from "react";
import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import IconBackgroundPattern from "Frontend/components/general/IconBackgroundPattern";
import {Card} from "@heroui/react";

interface LibraryHeaderProps {
    library: LibraryDto;
    className?: string;
}

export default function LibraryHeader({library, className}: LibraryHeaderProps) {
    const MAX_COVER_COUNT = 5;
    const state = useSnapshot(gameState);
    const randomGames = getRandomGames();

    function getRandomGames() {
        const games = state.randomlyOrderedGamesByLibraryId[library.id] as GameDto[];
        if (!games) return [];
        return games.slice(0, MAX_COVER_COUNT);
    }

    return (
        <Card className={`overflow-hidden rounded-lg relative pointer-events-none select-none ${className}`}>
            <IconBackgroundPattern/>
            <div className="flex flex-row items-center w-full h-full brightness-50">
                {randomGames.map((game, idx) => (
                    <div
                        key={idx}
                        className="flex-none overflow-hidden -ml-[10%]"
                        style={{
                            width: `calc(100% / ${MAX_COVER_COUNT - 2})`,
                            clipPath: 'polygon(15% 0, 100% 0, 85% 100%, 0% 100%)',
                        }}
                    >
                        <img
                            src={`/images/screenshot/${game.imageIds![0]}`}
                            alt={`Image ${idx}`}
                        />
                    </div>
                ))}
            </div>
            <div className="absolute inset-0 flex items-center justify-center">
                <h2 className="text-white text-3xl font-bold">{library.name}</h2>
            </div>
        </Card>
    );
}