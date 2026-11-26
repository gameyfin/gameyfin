import React, {useEffect, useLayoutEffect, useMemo, useRef, useState} from "react";
import {GameEndpoint} from "Frontend/generated/endpoints";
import {Link as RouterLink, useNavigate, useParams} from "react-router";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import GameDownloadButtons from "Frontend/components/general/input/GameDownloadButtons";
import ImageCarousel from "Frontend/components/general/covers/ImageCarousel";
import BlurhashImage from "Frontend/components/general/covers/BlurhashImage";
import {Button, Chip, Dropdown, Link, Separator, Surface, Tooltip, useOverlayState} from "@heroui/react";
import {isAdmin} from "Frontend/util/utils";
import {gameState, useFullGame} from "Frontend/state/GameState";
import {useCollectionIdsOfGame} from "Frontend/state/derived";
import {useSnapshot} from "valtio/react";
import {
    CaretLeftIcon,
    CaretRightIcon,
    CheckCircleIcon,
    DotsThreeIcon,
    InfoIcon,
    MagnifyingGlassIcon,
    PencilIcon,
    StackIcon,
    TrashIcon,
} from "@phosphor-icons/react";
import {useAuth} from "Frontend/util/auth";
import MatchGameModal from "Frontend/components/general/modals/MatchGameModal";
import GameVersionEditorModal from "Frontend/components/general/modals/GameVersionEditorModal";
import EditGameMetadataModal from "Frontend/components/general/modals/EditGameMetadataModal";
import GameUpdateDto from "Frontend/generated/org/gameyfin/app/games/dto/GameUpdateDto";
import type GameSummaryDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSummaryDto";
import MarkdownContent from "Frontend/components/general/MarkdownContent";
import ChipList from "Frontend/components/general/ChipList";
import {collectionState} from "Frontend/state/CollectionState";
import {GameMetadataAdminDto} from "Frontend/dtos/GameDtos";
import GameVersionDetails from "Frontend/components/general/versions/GameVersionDetails";
import {libraryState} from "Frontend/state/LibraryState.ts";
import {
    GameFavouriteButton,
    GamePlayStatus,
    GameRatingInput,
    GameReviews,
    GameScores,
    useGameSocial
} from "Frontend/components/social/GameSocial";
import RemoveGameModal from "Frontend/components/general/modals/RemoveGameModal";
import {findRelatedGames} from "Frontend/util/relatedGames";
import {NARROW_QUERY, useMediaQuery} from "Frontend/util/useMediaQuery";

function DiscoveryChips({items, filter, label}: {
    items?: string[];
    filter: "genre" | "theme" | "feature";
    label: string
}) {
    if (!items?.length) return null;
    return (
        <ul aria-label={label} className="flex flex-wrap gap-1.5">
            {[...items].sort().map(item => (
                <li key={item} className="min-w-0">
                    <Link href={`/search?${filter}=${encodeURIComponent(item)}`} className="max-w-full">
                        <Chip size="sm" className="h-auto min-h-6 whitespace-normal">{item}</Chip>
                    </Link>
                </li>
            ))}
        </ul>
    );
}

const coverAspectRatio = 12 / 17;

function RelatedGames({games, coverHeight}: { games: readonly GameSummaryDto[]; coverHeight: number }) {
    const row = useRef<HTMLUListElement>(null);
    const [layout, setLayout] = useState({count: 1, height: coverHeight});

    useLayoutEffect(() => {
        const list = row.current;
        if (!list) return;

        const update = () => {
            const {width} = list.getBoundingClientRect();
            const gap = parseFloat(getComputedStyle(list).columnGap) || 0;
            const height = Math.min(coverHeight, width / coverAspectRatio);
            if (height <= 0) return;
            const count = Math.max(1, Math.floor((width + gap) / (height * coverAspectRatio + gap)));
            setLayout(previous => previous.count === count && previous.height === height
                ? previous : {count, height});
        };

        update();
        const observer = new ResizeObserver(update);
        observer.observe(list);
        return () => observer.disconnect();
    }, [coverHeight]);

    return <section aria-labelledby="related-games" className="flex min-w-0 flex-col gap-4">
        <h2 id="related-games" className="text-xl font-semibold">Related games</h2>
        <ul ref={row} className="flex items-start justify-between gap-3">
            {games.slice(0, layout.count).map(game => (
                <li key={game.id} className="shrink-0" style={{width: layout.height * coverAspectRatio}}>
                    <RouterLink to={`/game/${game.id}`} aria-label={game.title}
                                className="group flex flex-col gap-2 rounded-sm hover:text-accent focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-focus">
                        <GameCover game={game} size={layout.height} lazy showTitle={false}/>
                        <span className="text-sm font-medium wrap-anywhere group-hover:underline">{game.title}</span>
                    </RouterLink>
                </li>
            ))}
        </ul>
    </section>;
}

export default function GameView() {
    const {gameId} = useParams();

    const navigate = useNavigate();
    const auth = useAuth();
    const admin = isAdmin(auth);
    const narrow = useMediaQuery(NARROW_QUERY);

    const versionsModal = useOverlayState();
    const editGameModal = useOverlayState();
    const matchGameModal = useOverlayState();
    const deleteModal = useOverlayState();

    const state = useSnapshot(gameState);
    const id = gameId ? parseInt(gameId) : undefined;
    const summary = id !== undefined ? state.state[id] : undefined;
    // Details are only loaded when the game is opened
    const {full: game} = useFullGame(summary ? id : undefined);
    const collections = useSnapshot(collectionState).state;
    const social = useGameSocial(game?.id);
    const collectionIds = useCollectionIdsOfGame(summary ? id : undefined);
    const gameCollections = collectionIds
        .map(collectionId => collections[collectionId])
        .filter(collection => collection !== undefined)
        .sort((a, b) => a.id - b.id);
    const libraries = useSnapshot(libraryState).state;
    const catalog = state.games;
    const relatedGames = useMemo(() => summary ? findRelatedGames(summary, catalog) : [], [summary, catalog]);

    const sortedGameIds = Object.keys(state.state).map(Number).sort((a, b) => a - b);
    const currentIndex = id !== undefined ? sortedGameIds.indexOf(id) : -1;

    function navigateToAdjacentGame(offset: number) {
        if (currentIndex < 0 || sortedGameIds.length === 0) return;
        const count = sortedGameIds.length;
        const targetIndex = (currentIndex + offset + count) % count;
        navigate(`/game/${sortedGameIds[targetIndex]}`);
    }

    useEffect(() => {
        if (state.isLoaded && !summary) {
            navigate("/", {replace: true});
        }
        document.title = summary ? summary.title : "Gameyfin";
    }, [gameId, state.isLoaded, summary]);

    useEffect(() => {
        window.scrollTo(0, 0);
    }, [gameId]);

    const matchConfirmed = admin && (game?.metadata as GameMetadataAdminDto | undefined)?.matchConfirmed;

    async function toggleMatchConfirmed() {
        if (!game) return;
        await GameEndpoint.updateGame(
            {
                id: game.id,
                metadata: {matchConfirmed: !matchConfirmed}
            } as GameUpdateDto
        );
    }

    return game && (
        <div className="isolate flex min-w-0 flex-col gap-6 -mt-4">
            <div className="overflow-hidden relative rounded-t-xl">
                {game.header?.id ? (
                    <BlurhashImage
                        className="w-full h-72 sm:h-96 object-cover brightness-50 blur-sm scale-110"
                        alt="Game header"
                        src={`/images/header/${game.header.id}`}
                        blurhash={game.header.blurhash}
                    />
                ) : game.images && game.images.length > 0 ? (
                    <BlurhashImage
                        className="w-full h-72 sm:h-96 object-cover brightness-50 blur-sm scale-110"
                        alt="Game screenshot"
                        src={`/images/screenshot/${game.images[0].id}`}
                        blurhash={game.images[0].blurhash}
                    />
                ) : (
                    <div className="w-full h-72 sm:h-96 bg-default relative"/>
                )}
                <div className="absolute inset-0 bg-linear-to-b from-transparent to-background"/>
            </div>
            <div
                className="pointer-events-none fixed inset-x-0 top-1/2 z-20 flex -translate-y-1/2 items-center justify-between pl-[max(0.5rem,env(safe-area-inset-left))] pr-[max(0.5rem,env(safe-area-inset-right))]">
                <Tooltip>
                    <Button isIconOnly variant="tertiary" className="pointer-events-auto size-11"
                            aria-label="Previous game" onPress={() => navigateToAdjacentGame(-1)}>
                        <CaretLeftIcon/>
                    </Button>
                    <Tooltip.Content>Previous game</Tooltip.Content>
                </Tooltip>
                <Tooltip>
                    <Button isIconOnly variant="tertiary" className="pointer-events-auto size-11"
                            aria-label="Next game" onPress={() => navigateToAdjacentGame(1)}>
                        <CaretRightIcon/>
                    </Button>
                    <Tooltip.Content>Next game</Tooltip.Content>
                </Tooltip>
            </div>
            <div className="mx-4 flex min-w-0 flex-col gap-10 wrap-anywhere sm:mx-8 xl:mx-24">
                <header
                    className="grid min-w-0 grid-cols-[auto_minmax(0,1fr)] items-end gap-4 sm:gap-x-8 lg:grid-cols-[auto_minmax(0,1fr)_auto]">
                    <div className="relative z-10 -mt-24 sm:-mt-65">
                        <GameCover game={game} size={narrow ? 144 : 320} radius="none" showTitle={false}/>
                    </div>
                    <div className="relative z-10 flex min-w-0 flex-col gap-1">
                        <h1 className="text-2xl! font-semibold sm:text-3xl!">{game.title}</h1>
                        {(game.release || game.platforms.length > 0) &&
                            <div className="flex items-end gap-3">
                                {game.release && <time dateTime={game.release} className="shrink-0 text-sm text-muted">
                                    {game.release.slice(0, 4)}
                                </time>}
                                <div className="min-w-0 [&_.chip]:whitespace-normal">
                                    <ChipList items={game.platforms} maxVisible={1}/>
                                </div>
                            </div>}
                    </div>
                    <div className="col-span-2 -mx-4 min-w-0 empty:hidden sm:mx-0 lg:col-span-1">
                        <GameDownloadButtons
                            key={game.id} gameId={game.id} metadata={game.metadata} compact={narrow}
                            leadingAction={(admin || social.memberId !== undefined) && <>
                                {admin && <Dropdown>
                                    <Button isIconOnly variant="tertiary" className="size-12 shrink-0"
                                            aria-label="Game administration">
                                        <DotsThreeIcon size={24} weight="bold" aria-hidden="true"/>
                                    </Button>
                                    <Dropdown.Popover placement="bottom end">
                                        <Dropdown.Menu aria-label="Game administration">
                                            <Dropdown.Item onAction={toggleMatchConfirmed}>
                                                <CheckCircleIcon aria-hidden="true"
                                                                 weight={matchConfirmed ? "fill" : "regular"}
                                                                 className={matchConfirmed ? "text-success" : undefined}/>
                                                {matchConfirmed ? "Unconfirm match" : "Confirm match"}
                                            </Dropdown.Item>
                                            <Dropdown.Item onAction={versionsModal.open}>
                                                <StackIcon aria-hidden="true"/>Versions &amp; Extras
                                            </Dropdown.Item>
                                            <Dropdown.Item onAction={editGameModal.open}>
                                                <PencilIcon aria-hidden="true"/>Edit metadata
                                            </Dropdown.Item>
                                            <Dropdown.Item onAction={matchGameModal.open}>
                                                <MagnifyingGlassIcon aria-hidden="true"/>Search for metadata
                                            </Dropdown.Item>
                                            <Dropdown.Item variant="danger" onAction={deleteModal.open}>
                                                <TrashIcon aria-hidden="true"/>Remove from library
                                            </Dropdown.Item>
                                        </Dropdown.Menu>
                                    </Dropdown.Popover>
                                </Dropdown>}
                                {social.memberId !== undefined && <GameFavouriteButton social={social}/>}
                            </>}
                        />
                    </div>
                </header>

                {game.comment?.trim() &&
                    <Surface render={(props) => <aside {...props} aria-labelledby="game-information-heading"/>}
                             variant="secondary" className="rounded-xl p-4">
                        <h3 id="game-information-heading"
                            className="mb-2 flex items-center gap-1 text-sm font-semibold">
                            <InfoIcon size={16} weight="fill" aria-hidden="true"/>
                            Information
                        </h3>
                        <MarkdownContent>{game.comment}</MarkdownContent>
                    </Surface>}

                <div className="grid min-w-0 gap-6 lg:grid-cols-[minmax(0,1fr)_max-content] lg:gap-12">
                    <section aria-labelledby="game-summary" className="flex min-w-0 flex-col gap-4">
                        <h2 id="game-summary" className="text-xl font-semibold">Summary</h2>
                        {game.summary?.trim() ?
                            <MarkdownContent
                                className="max-w-prose text-base text-justify lg:max-w-none">{game.summary}</MarkdownContent> :
                            <p className="text-muted">No summary available</p>}
                        <div className="flex flex-wrap gap-1.5 empty:hidden">
                            <DiscoveryChips items={game.genres} filter="genre" label="Genres"/>
                            <DiscoveryChips items={game.themes} filter="theme" label="Themes"/>
                        </div>
                    </section>
                    <div className="flex min-w-0 flex-col gap-4">
                        <GameScores data={social.data} critic={game.criticRating} external={game.userRating}/>
                        <GameRatingInput key={`${game.id}:${social.memberId}`} social={social}/>
                    </div>
                </div>

                {(Boolean(game.images?.length) || Boolean(game.videoUrls?.length)) &&
                    <section aria-labelledby="game-media" className="flex min-w-0 flex-col gap-4">
                        <h2 id="game-media" className="text-xl font-semibold">Media</h2>
                        <ImageCarousel
                            images={game.images?.map(image => ({
                                url: `/images/screenshot/${image.id}`,
                                blurhash: image.blurhash
                            }))}
                            videosUrls={game.videoUrls}
                            mediaOrder={game.mediaOrder}
                            className="-mx-4 sm:-mx-8 xl:-mx-24"
                        />
                    </section>}

                <div className="grid min-w-0 gap-8 lg:grid-cols-2 lg:gap-12">
                    <section aria-labelledby="game-details" className="min-w-0">
                        <h2 id="game-details" className="text-xl font-semibold">Details</h2>
                        <table
                            className="w-full table-fixed border-separate border-spacing-y-3 text-left text-sm [&_td]:align-top">
                            <tbody>
                            {game.developers && game.developers.length > 0 &&
                                <tr>
                                    <td className="text-muted w-32">Developed by</td>
                                    <td>
                                        <div className="flex flex-wrap gap-1">
                                            {[...game.developers].sort().map((dev, index) =>
                                                <React.Fragment key={dev}>
                                                    <Link href={`/search?dev=${encodeURIComponent(dev)}`}
                                                          className="text-foreground hover:underline">
                                                        {dev}
                                                    </Link>
                                                    {index < game.developers!.length - 1 &&
                                                        <span aria-hidden="true">/</span>}
                                                </React.Fragment>
                                            )}
                                        </div>
                                    </td>
                                </tr>}
                            {game.publishers && game.publishers.length > 0 &&
                                <tr>
                                    <td className="text-muted w-32">Published by</td>
                                    <td>{[...game.publishers].sort().join(" / ")}</td>
                                </tr>}
                            {Boolean(game.features?.length) &&
                                <tr>
                                    <td className="text-muted w-32">Features</td>
                                    <td><DiscoveryChips items={game.features} filter="feature" label="Features"/></td>
                                </tr>
                            }
                            <tr>
                                <td className="text-muted w-32">Library</td>
                                <td>
                                    <Link href={`/library/${game.libraryId}`}
                                          className="text-foreground hover:underline">
                                        {libraries[game.libraryId]?.name ?? "Unknown library"}
                                    </Link>
                                </td>
                            </tr>
                            {gameCollections.length > 0 &&
                                <tr>
                                    <td className="text-muted w-32">Collections</td>
                                    <td>
                                        <div className="flex flex-wrap gap-1">
                                            {gameCollections.map((collection, index) =>
                                                <React.Fragment key={collection.id}>
                                                    <Link href={`/collection/${collection.id}`}
                                                          className="text-foreground hover:underline">
                                                        {collection.name}{collection.ownerName && collection.ownerId !== auth.state.user?.id ? ` · ${collection.ownerName}` : ""}
                                                    </Link>
                                                    {index < gameCollections.length - 1 &&
                                                        <span aria-hidden="true">/</span>}
                                                </React.Fragment>
                                            )}
                                        </div>
                                    </td>
                                </tr>
                            }
                            <GameVersionDetails key={game.id} gameId={game.id} refreshKey={versionsModal.isOpen}/>
                            </tbody>
                        </table>
                    </section>
                    <div className="flex min-w-0 flex-col gap-6">
                        <GamePlayStatus social={social}/>
                        <GameReviews key={`${game.id}:${social.memberId}`} social={social}/>
                    </div>
                </div>

                <Separator/>

                {relatedGames.length > 0 && <RelatedGames games={relatedGames} coverHeight={narrow ? 160 : 196}/>}
            </div>
            {admin && <>
                <RemoveGameModal
                    game={{id: game.id, title: game.title, path: (game.metadata as GameMetadataAdminDto).path}}
                    isOpen={deleteModal.isOpen} onOpenChange={deleteModal.setOpen}
                    onRemoved={() => navigate("/")}/>
                {versionsModal.isOpen && <GameVersionEditorModal key={game.id} gameId={game.id}
                                                                 gameTitle={game.title} onClose={versionsModal.close}/>}
                <EditGameMetadataModal gameId={game.id}
                                       isOpen={editGameModal.isOpen}
                                       onOpenChange={editGameModal.setOpen}/>
                <MatchGameModal path={(game.metadata as GameMetadataAdminDto).path!}
                                libraryId={game.libraryId}
                                replaceGameId={game.id}
                                initialSearchTerm={game.title}
                                isOpen={matchGameModal.isOpen}
                                onOpenChange={matchGameModal.setOpen}/>
            </>}
        </div>
    );
}
