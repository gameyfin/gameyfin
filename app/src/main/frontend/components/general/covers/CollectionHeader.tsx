import CollectionAdminDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionAdminDto";
import React, {useEffect, useState} from "react";
import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import IconBackgroundPattern from "Frontend/components/general/IconBackgroundPattern";
import {Card} from "@heroui/react";

interface CollectionHeaderProps {
    collection: CollectionAdminDto;
    className?: string;
}

export default function CollectionHeader({collection, className}: CollectionHeaderProps) {
    const MAX_COVER_COUNT = 5;
    const state = useSnapshot(gameState);
    const [randomGames, setRandomGames] = useState<GameDto[]>([]);

    useEffect(() => {
        if (!state.randomlyOrderedGamesByCollectionId) return;
        setRandomGames(getRandomGames());
    }, [state]);

    function getRandomGames() {
        if (!state.randomlyOrderedGamesByCollectionId[collection.id]) return [];
        const games = state.randomlyOrderedGamesByCollectionId[collection.id]
            .filter(game => game.images && game.images.length > 0);
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
                            src={`/images/screenshot/${game.images![0].id}`}
                            alt={`Image ${idx}`}
                        />
                    </div>
                ))}
            </div>
            <div className="absolute inset-0 flex items-center justify-center">
                <h2 className="text-white text-3xl font-bold">{collection.name}</h2>
            </div>
        </Card>
    );
}