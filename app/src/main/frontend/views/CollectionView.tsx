import {useSnapshot} from "valtio/react";
import React, {useEffect} from "react";
import {useNavigate, useParams} from "react-router";
import CoverGrid from "Frontend/components/general/covers/CoverGrid";
import {collectionState} from "Frontend/state/CollectionState";
import {useGamesByCollectionId} from "Frontend/state/derived";

export default function CollectionView() {
    const {collectionId} = useParams();
    const navigate = useNavigate();
    const collections = useSnapshot(collectionState);
    const games = useGamesByCollectionId()[parseInt(collectionId!)] ?? [];

    useEffect(() => {
        window.scrollTo(0, 0)
    }, [])

    useEffect(() => {
        if (collections.isLoaded && (!collectionId || !collections.state[parseInt(collectionId)])) {
            navigate("/", {replace: true});
        }
        document.title = collections.state[parseInt(collectionId!)]?.name || "Gameyfin";
    }, [collectionId, collections]);

    return (
        <div className="flex flex-col gap-6">
            <p className="text-4xl font-bold text-center">{collections.state[parseInt(collectionId!)]?.name}</p>
            <CoverGrid games={games}/>
            {games.length === 0 && <p className="text-center text-gray-500">This collection is empty.</p>}
        </div>
    );
}