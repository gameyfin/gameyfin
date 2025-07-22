import {useSnapshot} from "valtio/react";
import {libraryState} from "Frontend/state/LibraryState";
import {gameState} from "Frontend/state/GameState";
import React, {useEffect} from "react";
import {useNavigate, useParams} from "react-router";
import CoverGrid from "Frontend/components/general/covers/CoverGrid";
import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";

export default function LibraryView() {
    const {libraryId} = useParams();
    const navigate = useNavigate();
    const libraries = useSnapshot(libraryState);
    const games = (libraryId ? useSnapshot(gameState).gamesByLibraryId[parseInt(libraryId!!)] || [] : []) as GameDto[];

    useEffect(() => {
        if (libraries.isLoaded && (!libraryId || !libraries.state[parseInt(libraryId)])) {
            navigate("/", {replace: true});
        }
        document.title = libraries.state[parseInt(libraryId!!)]?.name || "Gameyfin";
    }, [libraryId, libraries]);

    return (
        <div className="flex flex-col gap-6">
            <p className="text-4xl font-bold text-center">{libraries.state[parseInt(libraryId!!)]?.name}</p>
            <CoverGrid games={games}/>
        </div>
    );
}