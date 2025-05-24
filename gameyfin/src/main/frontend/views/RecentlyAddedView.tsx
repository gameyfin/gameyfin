import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import React from "react";
import CoverGrid from "Frontend/components/general/covers/CoverGrid";

export default function RecentlyAddedView() {
    const games = useSnapshot(gameState).recentlyAdded as GameDto[];

    return (
        <div className="flex flex-col gap-4">
            <p className="text-4xl font-bold text-center">Recently added</p>
            <CoverGrid games={games}/>
        </div>
    );
}