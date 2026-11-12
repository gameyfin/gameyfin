import Popup from "Frontend/components/general/Popup";
import React, {useEffect, useState} from "react";
import {DownloadProviderEndpoint, GameEndpoint} from "Frontend/generated/endpoints";
import {useNavigate, useParams} from "react-router";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import ComboButton, {ComboButtonOption} from "Frontend/components/general/input/ComboButton";
import ImageCarousel from "Frontend/components/general/covers/ImageCarousel";
import BlurhashImage from "Frontend/components/general/covers/BlurhashImage";
import {Button, Chip, Link, Surface, toast, Tooltip, useOverlayState} from "@heroui/react";
import {humanFileSize, isAdmin, starRatingAsString} from "Frontend/util/utils";
import {DownloadEndpoint} from "Frontend/endpoints/endpoints";
import {gameState, useFullGame} from "Frontend/state/GameState";
import {useCollectionIdsOfGame} from "Frontend/state/derived";
import {useSnapshot} from "valtio/react";
import {
    CaretLeftIcon,
    CaretRightIcon,
    CheckCircleIcon,
    DownloadSimpleIcon,
    InfoIcon,
    MagnifyingGlassIcon,
    PencilIcon,
    StarIcon,
    TrashIcon,
} from "@phosphor-icons/react";
import {useAuth} from "Frontend/util/auth";
import MatchGameModal from "Frontend/components/general/modals/MatchGameModal";
import EditGameMetadataModal from "Frontend/components/general/modals/EditGameMetadataModal";
import GameUpdateDto from "Frontend/generated/org/gameyfin/app/games/dto/GameUpdateDto";
import MarkdownContent from "Frontend/components/general/MarkdownContent";
import ChipList from "Frontend/components/general/ChipList";
import {collectionState} from "Frontend/state/CollectionState";
import {GameMetadataAdminDto} from "Frontend/dtos/GameDtos";
import {libraryState} from "Frontend/state/LibraryState.ts";
import ConfirmModal from "Frontend/components/general/modals/ConfirmModal";

export default function GameView() {
    const {gameId} = useParams();

    const navigate = useNavigate();
    const auth = useAuth();

    const editGameModal = useOverlayState();
    const matchGameModal = useOverlayState();
    const deleteModal = useOverlayState();
    const [isDeleting, setIsDeleting] = useState(false);

    const state = useSnapshot(gameState);
    const id = gameId ? parseInt(gameId) : undefined;
    const summary = id !== undefined ? state.state[id] : undefined;
    // Details are only loaded when the game is opened
    const {full: game} = useFullGame(summary ? id : undefined);
    const collections = useSnapshot(collectionState).state;
    const collectionIds = useCollectionIdsOfGame(summary ? id : undefined);
    const libraries = useSnapshot(libraryState).state;

    const [downloadOptions, setDownloadOptions] = useState<Record<string, ComboButtonOption>>();

    const sortedGameIds = Object.keys(state.state).map(Number).sort((a, b) => a - b);
    const currentIndex = id !== undefined ? sortedGameIds.indexOf(id) : -1;

    function navigateToAdjacentGame(offset: number) {
        if (currentIndex < 0 || sortedGameIds.length === 0) return;
        const count = sortedGameIds.length;
        const targetIndex = (currentIndex + offset + count) % count;
        navigate(`/game/${sortedGameIds[targetIndex]}`);
    }

    useEffect(() => {
        DownloadProviderEndpoint.getProviders().then((providers) => {
            const options: Record<string, ComboButtonOption> = providers.reduce((acc, provider) => {
                acc[provider.key] = {
                    label: provider.name,
                    description: provider.shortDescription ?? provider.description,
                    action: () => {
                        if (gameId) DownloadEndpoint.downloadGame(parseInt(gameId), provider.key);
                    },
                };
                return acc;
            }, {} as Record<string, ComboButtonOption>);
            setDownloadOptions(options);
        });
    }, [gameId]);

    useEffect(() => {
        if (state.isLoaded && !summary) {
            navigate("/", {replace: true});
        }
        document.title = summary ? summary.title : "Gameyfin";
    }, [gameId, state.isLoaded, summary]);

    async function toggleMatchConfirmed() {
        if (!game) return;
        await GameEndpoint.updateGame(
            {
                id: game.id,
                metadata: {matchConfirmed: !(game.metadata as GameMetadataAdminDto).matchConfirmed}
            } as GameUpdateDto
        );
    }

    async function deleteGame() {
        if (!game) return;
        setIsDeleting(true);
        try {
            await GameEndpoint.deleteGame(game.id);
            toast.success("Game deleted", {
                description: `${game.title} removed from Gameyfin!`,
            });
            deleteModal.close();
            navigate("/");
        } catch (e) {
            toast.warning("Error deleting game", {
                description: `${game.title} could not be removed from Gameyfin!`,
            });
        } finally {
            setIsDeleting(false);
        }
    }

    return game && (
        <div className="flex flex-col gap-4">
            <div className="overflow-hidden relative rounded-t-xl">
                {game.header?.id ? (
                    <BlurhashImage
                        className="w-full h-96 object-cover brightness-50 blur-sm scale-110"
                        alt="Game header"
                        src={`/images/header/${game.header.id}`}
                        blurhash={game.header.blurhash}
                    />
                ) : game.images && game.images.length > 0 ? (
                    <BlurhashImage
                        className="w-full h-96 object-cover brightness-50 blur-sm scale-110"
                        alt="Game screenshot"
                        src={`/images/screenshot/${game.images[0].id}`}
                        blurhash={game.images[0].blurhash}
                    />
                ) : (
                    <div className="w-full h-96 bg-default relative"/>
                )}
                <div className="absolute inset-0 bg-linear-to-b from-transparent to-background"/>
            </div>
            <div className="flex min-w-0 flex-col gap-4 mx-4 sm:mx-8 xl:mx-24">
                <div className="flex flex-wrap items-start gap-4">
                    <div className="flex min-w-0 flex-1 basis-lg flex-col gap-4 sm:flex-row">
                        <div className="-mt-65 relative z-10 shrink-0 self-start">
                            <GameCover game={game} size={320} radius="none" showTitle={false}/>
                        </div>
                        <div className="flex min-w-0 flex-1 flex-col gap-1">
                            <div className="flex flex-wrap gap-x-4 gap-y-1 items-baseline">
                                <p className="min-w-0 wrap-anywhere font-semibold text-2xl sm:text-3xl">
                                    {game.title}
                                </p>
                                {(game.criticRating || game.userRating) &&
                                    <div className="flex shrink-0 items-center gap-1 mb-0.5 text-muted">
                                        <StarIcon weight="fill"/>
                                        {starRatingAsString(game)}
                                    </div>
                                }
                            </div>
                            <div className="flex flex-wrap items-center gap-2">
                                <p className="text-muted">
                                    {game.release !== undefined ? new Date(game.release).getFullYear() :
                                        <span className="text-muted">no data</span>}
                                </p>
                                <ChipList items={game.platforms} maxVisible={1}/>
                                <Popup>
                                    <Popup.Trigger>
                                        <InfoIcon/>
                                    </Popup.Trigger>
                                    <Popup.Content placement="right">
                                        {`Last update: ${new Date(game.updatedAt).toLocaleString()}`}
                                    </Popup.Content>
                                </Popup>
                            </div>
                        </div>
                    </div>
                    <div className="flex w-full sm:w-auto max-w-full flex-wrap items-center gap-4 xl:gap-8">
                        <div className="flex shrink-0 flex-row gap-2">
                            <Tooltip>
                                <Tooltip.Trigger>
                                    <Button isIconOnly variant="tertiary" aria-label="Previous game"
                                            onPress={() => navigateToAdjacentGame(-1)}>
                                        <CaretLeftIcon/>
                                    </Button>
                                </Tooltip.Trigger>
                                <Tooltip.Content>Previous game</Tooltip.Content>
                            </Tooltip>
                            <Tooltip>
                                <Tooltip.Trigger>
                                    <Button isIconOnly variant="tertiary" aria-label="Next game"
                                            onPress={() => navigateToAdjacentGame(1)}>
                                        <CaretRightIcon/>
                                    </Button>
                                </Tooltip.Trigger>
                                <Tooltip.Content>Next game</Tooltip.Content>
                            </Tooltip>
                        </div>
                        {isAdmin(auth) && <div className="flex shrink-0 flex-row gap-2">
                            <Tooltip>
                                <Tooltip.Trigger>
                                    <Button isIconOnly variant="tertiary" onPress={toggleMatchConfirmed}>
                                        {(game.metadata as GameMetadataAdminDto).matchConfirmed ?
                                            <CheckCircleIcon weight="fill" className="fill-success"/> :
                                            <CheckCircleIcon/>}
                                    </Button>
                                </Tooltip.Trigger>
                                <Tooltip.Content>
                                    {(game.metadata as GameMetadataAdminDto).matchConfirmed ? "Unconfirm match" : "Confirm match"}
                                </Tooltip.Content>
                            </Tooltip>
                            <Tooltip>
                                <Tooltip.Trigger>
                                    <Button isIconOnly variant="tertiary" onPress={editGameModal.open}>
                                        <PencilIcon/>
                                    </Button>
                                </Tooltip.Trigger>
                                <Tooltip.Content>Edit metadata</Tooltip.Content>
                            </Tooltip>
                            <Tooltip>
                                <Tooltip.Trigger>
                                    <Button isIconOnly variant="tertiary" onPress={matchGameModal.open}>
                                        <MagnifyingGlassIcon/>
                                    </Button>
                                </Tooltip.Trigger>
                                <Tooltip.Content>Search for metadata</Tooltip.Content>
                            </Tooltip>
                            <Tooltip>
                                <Tooltip.Trigger>
                                    <Button isIconOnly variant="danger" onPress={deleteModal.open}>
                                        <TrashIcon/>
                                    </Button>
                                </Tooltip.Trigger>
                                <Tooltip.Content>Remove from library</Tooltip.Content>
                            </Tooltip>
                        </div>}
                        {downloadOptions &&
                            <ComboButton className="w-full sm:w-auto" icon={<DownloadSimpleIcon size={24}/>}
                                         description={humanFileSize(game.metadata.fileSize)}
                                         options={downloadOptions}
                                         preferredOptionKey="preferred-download-method"
                            />}
                    </div>
                </div>
                <div className="flex min-w-0 flex-col gap-8 wrap-anywhere">
                    {game.comment?.trim() &&
                        <Surface render={(props) => <section {...props} aria-labelledby="game-information-heading"/>}
                                 variant="secondary"
                                 className="min-w-0 rounded-xl p-4 sm:p-5">
                            <h4 id="game-information-heading"
                                className="mb-3 flex items-center gap-2 text-sm font-semibold text-foreground">
                                <InfoIcon size={14} weight="fill" aria-hidden="true"/>
                                Information
                            </h4>
                            <MarkdownContent className="text-foreground">{game.comment}</MarkdownContent>
                        </Surface>
                    }
                    <div className="flex flex-col gap-8 lg:flex-row lg:gap-12">
                        <div className="flex min-w-0 flex-col flex-1 gap-2">
                            <p className="text-muted">Summary</p>
                            {game.summary ?
                                <MarkdownContent className="text-justify">{game.summary}</MarkdownContent> :
                                <p>No summary available</p>
                            }
                        </div>
                        <div className="flex min-w-0 flex-col flex-1">
                            <p className="text-muted">Details</p>
                            <table className="text-left w-full table-fixed border-separate border-spacing-y-1">
                                <tbody>
                                {game.developers && game.developers.length > 0 &&
                                    <tr>
                                        <td className="text-muted w-32">Developed by</td>
                                        <td className="flex flex-wrap gap-1">
                                            {
                                                [...game.developers].sort().map((dev, index) =>
                                                    <React.Fragment key={dev}>
                                                        <Link href={`/search?dev=${encodeURIComponent(dev)}`}
                                                              className="text-foreground hover:underline">
                                                            {dev}
                                                        </Link>
                                                        {index !== game.developers!.length - 1 && <p>/</p>}
                                                    </React.Fragment>
                                                )
                                            }
                                        </td>
                                    </tr>}
                                {game.publishers && game.publishers.length > 0 &&
                                    <tr>
                                        <td className="text-muted w-32">Published by</td>
                                        <td>
                                            {
                                                [...game.publishers].sort().join(" / ")
                                            }
                                        </td>
                                    </tr>}
                                {game.genres && game.genres.length > 0 &&
                                    <tr>
                                        <td className="text-muted w-32">Genres</td>
                                        <td className="flex flex-wrap gap-1">
                                            {
                                                [...game.genres].sort().map((genre) =>
                                                    <Link key={genre}
                                                          href={`/search?genre=${encodeURIComponent(genre)}`}>
                                                        <Chip size="sm" className="text-sm">
                                                            {genre}
                                                        </Chip>
                                                    </Link>
                                                )
                                            }
                                        </td>
                                    </tr>}
                                {game.themes && game.themes.length > 0 &&
                                    <tr>
                                        <td className="text-muted w-32">Themes</td>
                                        <td className="flex flex-wrap gap-1">
                                            {
                                                [...game.themes].sort().map((theme) =>
                                                    <Link key={theme}
                                                          href={`/search?theme=${encodeURIComponent(theme)}`}>
                                                        <Chip size="sm" className="text-sm">
                                                            {theme}
                                                        </Chip>
                                                    </Link>
                                                )
                                            }
                                        </td>
                                    </tr>}
                                {game.features && game.features.length > 0 &&
                                    <tr>
                                        <td className="text-muted w-32">Features</td>
                                        <td className="flex flex-wrap gap-1">
                                            {
                                                [...game.features].sort().map((feature) =>
                                                    <Link key={feature}
                                                          href={`/search?feature=${encodeURIComponent(feature)}`}>
                                                        <Chip size="sm" className="text-sm">
                                                            {feature}
                                                        </Chip>
                                                    </Link>
                                                )
                                            }
                                        </td>
                                    </tr>}
                                <tr>
                                    <td className="text-muted w-32">Library</td>
                                    <td>
                                        <Link href={`/library/${game.libraryId}`}
                                              className="text-foreground hover:underline">
                                            {libraries[game.libraryId]?.name ?? "Unknown library"}
                                        </Link>
                                    </td>
                                </tr>
                                {collectionIds.length > 0 &&
                                    <tr>
                                        <td className="text-muted w-32">Collections</td>
                                        <td className="flex flex-wrap gap-1">
                                            {collectionIds
                                                .map((collectionId) => collections[collectionId])
                                                .filter((collection) => collection !== undefined)
                                                .sort((a, b) => a.id - b.id)
                                                .map((collection, index) =>
                                                    <React.Fragment key={collection.id}>
                                                        <Link href={`/collection/${collection.id}`}
                                                              className="text-foreground hover:underline">
                                                            {collection.name}
                                                        </Link>
                                                        {index !== collectionIds.length - 1 && <p>/</p>}
                                                    </React.Fragment>
                                                )}
                                        </td>
                                    </tr>
                                }
                                </tbody>
                            </table>
                        </div>
                    </div>
                    <div className="flex flex-col gap-4">
                        <p className="text-muted">Media</p>
                        <ImageCarousel
                            images={game.images?.map(image => ({
                                url: `/images/screenshot/${image.id}`,
                                blurhash: image.blurhash
                            }))}
                            videosUrls={game.videoUrls}
                            mediaOrder={game.mediaOrder}
                            className="-mx-4 sm:-mx-8 xl:-mx-24"
                        />
                    </div>
                </div>
            </div>
            {isAdmin(auth) && <>
                <ConfirmModal isOpen={deleteModal.isOpen}
                              onOpenChange={deleteModal.setOpen}
                              title="Remove from library"
                              confirmLabel="Remove"
                              isPending={isDeleting}
                              onConfirm={deleteGame}>
                    <p>Remove <code>{game.title}</code> from the library? This can not be undone.</p>
                </ConfirmModal>
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
