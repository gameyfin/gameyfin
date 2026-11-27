import {Button, Surface} from "@heroui/react";
import {SlidersHorizontalIcon} from "@phosphor-icons/react";
import {useEffect} from "react";
import {useNavigate, useParams} from "react-router";
import {useSnapshot} from "valtio/react";
import CoverGrid from "Frontend/components/general/covers/CoverGrid";
import CollectionKind from "Frontend/generated/org/gameyfin/app/collections/entities/CollectionKind";
import {collectionState} from "Frontend/state/CollectionState";
import {useGamesByCollectionId} from "Frontend/state/derived";
import {gameState} from "Frontend/state/GameState";
import {libraryState} from "Frontend/state/LibraryState";
import {useMyPlayStatuses} from "Frontend/state/useMyPlayStatuses";
import {useAuth} from "Frontend/util/auth";
import {isAdmin} from "Frontend/util/utils";

export default function GameBrowseView() {
    const {libraryId, collectionId} = useParams();
    const navigate = useNavigate();
    const libraries = useSnapshot(libraryState);
    const collections = useSnapshot(collectionState);
    const catalog = useSnapshot(gameState);
    const collectionGames = useGamesByCollectionId();
    const auth = useAuth();
    const memberId = auth.state.user?.id;
    const isLibrary = collectionId === undefined;
    const kind = isLibrary ? "library" : "collection";
    const playStatuses = useMyPlayStatuses(isLibrary)?.statuses;
    const library = isLibrary ? libraries.state[Number(libraryId)] : undefined;
    const collection = isLibrary ? undefined : collections.state[Number(collectionId)];
    const name = library?.name ?? collection?.name;
    const isLoaded = isLibrary ? libraries.isLoaded : collections.isLoaded;
    const games = (isLibrary ? catalog.gamesByLibraryId[Number(libraryId)] : collectionGames[Number(collectionId)]) ?? [];

    useEffect(() => {
        window.scrollTo(0, 0);
    }, [kind, libraryId, collectionId]);

    useEffect(() => {
        document.title = name ?? "Gameyfin";
    }, [name]);

    useEffect(() => {
        if (isLibrary && isLoaded && !library) navigate("/", {replace: true});
    }, [isLibrary, isLoaded, library, navigate]);

    if (!isLoaded) return <p role="status" className="text-center text-muted">Loading {kind}…</p>;
    if (isLibrary && !library) return null;
    if (!isLibrary && !collection) return <p className="text-center text-muted">Collection unavailable.</p>;

    let managementPath: string | undefined;
    if (library) {
        managementPath = `/administration/games/library/${library.id}`;
    } else if (collection?.kind === CollectionKind.CURATED) {
        managementPath = `/administration/games/collection/${collection.id}`;
    } else if (collection && memberId !== undefined && collection.ownerId === memberId) {
        managementPath = `/collections/${collection.id}/manage`;
    }

    return (
        <div className="flex min-w-0 flex-col gap-6">
            <Surface className="flex flex-wrap items-start justify-between gap-4 rounded-2xl p-4 sm:p-6">
                <div className="flex min-w-0 flex-1 basis-48 flex-col gap-2">
                    <h1 className="text-2xl font-semibold wrap-anywhere sm:text-3xl">{name}</h1>
                    {(catalog.isLoaded || collection?.ownerName) &&
                        <div className="flex flex-wrap gap-x-4 gap-y-1 text-sm text-muted">
                            {catalog.isLoaded && <span className="tabular-nums">
                            {games.length} {games.length === 1 ? "game" : "games"}
                        </span>}
                            {collection?.ownerName && <span className="wrap-anywhere">By {collection.ownerName}</span>}
                        </div>}
                    {collection?.description && <p className="max-w-prose whitespace-pre-wrap wrap-anywhere">
                        {collection.description}
                    </p>}
                </div>
                {isAdmin(auth) && managementPath &&
                    <Button variant="secondary" className="ml-auto" onPress={() => navigate(managementPath)}>
                        <SlidersHorizontalIcon aria-hidden="true"/>
                        Manage {kind}
                    </Button>}
            </Surface>
            {games.length > 0 ? <CoverGrid games={games} playStatuses={playStatuses}/> :
                <p role="status" className="text-center text-muted">
                    {catalog.isLoaded ? `This ${kind} is empty.` : "Loading games…"}
                </p>}
        </div>
    );
}
