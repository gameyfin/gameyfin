import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryDto";
import React, {useEffect, useState} from "react";
import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import {randomGamesFromLibrary} from "Frontend/util/utils";

interface LibraryHeaderProps {
    library: LibraryDto;
    className?: string;
}

export default function LibraryHeader({library, className}: LibraryHeaderProps) {
    const [randomGames, setRandomGames] = useState<GameDto[]>([]);
    const maxCoverCount = 5;

    useEffect(() => {
        randomGamesFromLibrary(library, maxCoverCount).then((games) => {
            setRandomGames(games);
        });
    }, [library, maxCoverCount]);

    return (
        <div className={`overflow-hidden rounded-lg relative pointer-events-none select-none ${className}`}>
            <div className="flex flex-row items-center w-full h-full brightness-50">
                {randomGames
                    .map((game, idx) => (
                        <div
                            key={idx}
                            className="flex-none overflow-hidden -ml-[10%]"
                            style={{
                                width: `calc(100% / ${maxCoverCount - 2})`,
                                clipPath: 'polygon(15% 0, 100% 0, 85% 100%, 0% 100%)',
                            }}
                        >
                            <img
                                src={`/images/screenshot/${game.imageIds![0]}`}
                                alt={`Image ${idx}`}
                            />
                        </div>
                    ))
                    .slice(0, maxCoverCount)}
            </div>
            <div className="absolute inset-0 flex items-center justify-center">
                <h2 className="text-white text-3xl font-bold">{library.name}</h2>
            </div>
        </div>
    );
}