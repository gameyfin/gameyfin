import {Link} from "@heroui/react";
import {useSnapshot} from "valtio/react";
import {useEffect} from "react";
import {useParams} from "react-router";
import CoverGrid from "Frontend/components/general/covers/CoverGrid";
import {collectionState} from "Frontend/state/CollectionState";
import {useGamesByCollectionId} from "Frontend/state/derived";
import {useAuth} from "Frontend/util/auth";

export default function CollectionView() {
    const {collectionId} = useParams();
    const id = Number(collectionId);
    const collections = useSnapshot(collectionState);
    const collection = collections.state[id];
    const games = useGamesByCollectionId()[id] ?? [];
    const memberId = useAuth().state.user?.id;

    useEffect(() => {
        window.scrollTo(0, 0);
    }, [id]);

    useEffect(() => {
        document.title = collection?.name ?? "Gameyfin";
    }, [collection?.name]);

    if (!collections.isLoaded) return <p className="text-center text-muted">Loading collection…</p>;
    if (!collection) return <p className="text-center text-muted">Collection unavailable.</p>;

    return <div className="flex flex-col gap-6">
        <div className="flex flex-col items-center gap-2 px-4 text-center">
            <h1 className="text-3xl font-bold wrap-anywhere">{collection.name}</h1>
            {collection.ownerName && <p className="text-muted">By {collection.ownerName}</p>}
            {collection.description && <p className="whitespace-pre-wrap wrap-anywhere">{collection.description}</p>}
            {memberId !== undefined && collection.ownerId === memberId &&
                <Link href={`/collections/${id}/manage`}>Manage collection</Link>}
        </div>
        <CoverGrid games={games}/>
        {games.length === 0 && <p className="text-center text-muted">This collection is empty.</p>}
    </div>;
}
