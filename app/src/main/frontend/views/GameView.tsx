import React, {useEffect, useState} from "react";
import {DownloadProviderEndpoint, GameEndpoint} from "Frontend/generated/endpoints";
import {useNavigate, useParams} from "react-router";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import ComboButton, {ComboButtonOption} from "Frontend/components/general/input/ComboButton";
import ImageCarousel from "Frontend/components/general/covers/ImageCarousel";
import {Accordion, Button, Chip, Link, Tooltip, toast, useOverlayState} from "@heroui/react";
import {humanFileSize, isAdmin, starRatingAsString} from "Frontend/util/utils";
import {DownloadEndpoint} from "Frontend/endpoints/endpoints";
import {gameState} from "Frontend/state/GameState";
import {useSnapshot} from "valtio/react";
import {
    CheckCircleIcon,
    InfoIcon,
    MagnifyingGlassIcon,
    PencilIcon,
    StarIcon,
    TrashIcon,
    TriangleDashedIcon,
} from "@phosphor-icons/react";
import {useAuth} from "Frontend/util/auth";
import MatchGameModal from "Frontend/components/general/modals/MatchGameModal";
import EditGameMetadataModal from "Frontend/components/general/modals/EditGameMetadataModal";
import GameUpdateDto from "Frontend/generated/org/gameyfin/app/games/dto/GameUpdateDto";
import Markdown from "react-markdown";
import remarkBreaks from "remark-breaks";
import ChipList from "Frontend/components/general/ChipList";
import {collectionState} from "Frontend/state/CollectionState";
import {GameMetadataAdminDto} from "Frontend/dtos/GameDtos";

export default function GameView() {
    const {gameId} = useParams();

    const navigate = useNavigate();
    const auth = useAuth();

    const editGameModal = useOverlayState();
    const matchGameModal = useOverlayState();

    const state = useSnapshot(gameState);
    const game = gameId ? state.state[parseInt(gameId)] : undefined;
    const collections = useSnapshot(collectionState).state;

    const [downloadOptions, setDownloadOptions] = useState<Record<string, ComboButtonOption>>();

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
        if (state.isLoaded && (!gameId || !state.state[parseInt(gameId)])) {
            navigate("/", {replace: true});
        }
        document.title = game ? game.title : "Gameyfin";
    }, [gameId, state]);

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
        await GameEndpoint.deleteGame(game.id);
        toast.success("Game deleted", {
            description: `${game.title} removed from Gameyfin!`,
        });
    }

    return game && (
        <div className="flex flex-col gap-4">
            <div className="overflow-hidden relative rounded-t-lg">
                {game.header?.id ? (
                    <img
                        className="w-full h-96 object-cover brightness-50 blur-sm scale-110"
                        alt="Game header"
                        src={`/images/header/${game.header?.id}`}
                    />
                ) : game.images && game.images.length > 0 ? (
                    <img
                        className="w-full h-96 object-cover brightness-50 blur-sm scale-110"
                        alt="Game screenshot"
                        src={`/images/screenshot/${game.images[0].id}`}
                    />
                ) : (
                    <div className="w-full h-96 bg-default relative"/>
                )}
                <div className="absolute inset-0 bg-linear-to-b from-transparent to-background"/>
            </div>
            <div className="flex flex-col gap-4 mx-24">
                <div className="flex flex-row justify-between">
                    <div className="flex flex-row gap-4">
                        <div className="-mt-65">
                            <GameCover game={game} size={320} radius="none"/>
                        </div>
                        <div className="flex flex-col gap-1">
                            <div className="flex flex-row gap-4 items-end">
                                <p className="font-semibold text-3xl">
                                    {game.title}
                                </p>
                                <div className="flex flex-row gap-1 mb-0.5 text-muted">
                                    <StarIcon weight="fill"/>
                                    {starRatingAsString(game)}
                                </div>
                            </div>
                            <div className="flex flex-row items-center gap-2">
                                <p className="text-muted">
                                    {game.release !== undefined ? new Date(game.release).getFullYear() :
                                        <span className="text-muted">no data</span>}
                                </p>
                                <ChipList items={game.platforms} maxVisible={1}/>
                                <Tooltip>
                                    <Tooltip.Trigger>
                                        <InfoIcon/>
                                    </Tooltip.Trigger>
                                    <Tooltip.Content placement="right">
                                        {`Last update: ${new Date(game.updatedAt).toLocaleString()}`}
                                    </Tooltip.Content>
                                </Tooltip>
                            </div>
                        </div>
                    </div>
                    <div className="flex flex-row items-center gap-8">
                        {isAdmin(auth) && <div className="flex flex-row gap-2">
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
                                    <Button isIconOnly variant="danger" onPress={async () => {
                                        await deleteGame();
                                        navigate("/");
                                    }}>
                                        <TrashIcon/>
                                    </Button>
                                </Tooltip.Trigger>
                                <Tooltip.Content>Remove from library</Tooltip.Content>
                            </Tooltip>
                        </div>}
                        {downloadOptions && <ComboButton description={humanFileSize(game.metadata.fileSize)}
                                                         options={downloadOptions}
                                                         preferredOptionKey="preferred-download-method"
                        />}
                    </div>
                </div>
                <div className="flex flex-col gap-8">
                    {game.comment &&
                        <Accordion variant="default" className="space-y-2">
                            <Accordion.Item id="information" className="-mx-2 rounded-lg bg-surface-secondary">
                                <Accordion.Heading>
                                    <Accordion.Trigger className="flex items-center justify-between font-bold">
                                        <span className="flex items-center gap-2">
                                            <InfoIcon weight="fill"/>
                                            Information
                                        </span>
                                        <Accordion.Indicator/>
                                    </Accordion.Trigger>
                                </Accordion.Heading>
                                <Accordion.Panel>
                                    <Accordion.Body className="mx-8 mb-4">
                                        <Markdown
                                            remarkPlugins={[remarkBreaks]}
                                            components={{
                                                a(props) {
                                                    return <Link href={props.href}
                                                                 target="_blank"
                                                                 rel="noopener noreferrer"
                                                                 className="text-foreground underline text-sm inline-flex items-center gap-1">
                                                        {props.children}
                                                        <Link.Icon/>
                                                    </Link>;
                                                }
                                            }}
                                        >{game.comment}</Markdown>
                                    </Accordion.Body>
                                </Accordion.Panel>
                            </Accordion.Item>
                        </Accordion>
                    }
                    <div className="flex flex-row gap-12">
                        <div className="flex flex-col flex-1 gap-2">
                            <p className="text-muted">Summary</p>
                            {game.summary ?
                                <div className="text-justify" dangerouslySetInnerHTML={{__html: game.summary}}/> :
                                <p>No summary available</p>
                            }
                        </div>
                        <div className="flex flex-col flex-1">
                            <p className="text-muted">Details</p>
                            <table className="text-left w-full table-auto border-separate border-spacing-y-1">
                                <tbody>
                                <tr>
                                    <td className="text-muted w-0 min-w-32">Developed by</td>
                                    <td className="flex flex-row gap-1">
                                        {game.developers && game.developers.length > 0
                                            ? [...game.developers].sort().map((dev, index) =>
                                                <React.Fragment key={dev}>
                                                    <Link href={`/search?dev=${encodeURIComponent(dev)}`} className="text-foreground hover:underline">
                                                        {dev}
                                                    </Link>
                                                    {index !== game.developers!.length - 1 && <p>/</p>}
                                                </React.Fragment>
                                            )
                                            : <Tooltip>
                                                <Tooltip.Trigger>
                                                    <TriangleDashedIcon className="fill-muted h-6 bottom-0"/>
                                                </Tooltip.Trigger>
                                                <Tooltip.Content placement="right">Missing data</Tooltip.Content>
                                            </Tooltip>
                                        }
                                    </td>
                                </tr>
                                <tr>
                                    <td className="text-muted w-0 min-w-32">Published by</td>
                                    <td className="flex flex-row gap-1">
                                        {game.publishers && game.publishers.length > 0
                                            ? [...game.publishers].sort().join(" / ")
                                            : <Tooltip>
                                                <Tooltip.Trigger>
                                                    <TriangleDashedIcon className="fill-muted h-6 bottom-0"/>
                                                </Tooltip.Trigger>
                                                <Tooltip.Content placement="right">Missing data</Tooltip.Content>
                                            </Tooltip>
                                        }
                                    </td>
                                </tr>
                                <tr>
                                    <td className="text-muted w-0 min-w-32">Genres</td>
                                    <td className="flex flex-row gap-1">
                                        {game.genres && game.genres.length > 0
                                            ? [...game.genres].sort().map((genre) =>
                                                <Link key={genre} href={`/search?genre=${encodeURIComponent(genre)}`}>
                                                    <Chip size="sm" className="text-sm">
                                                        {genre}
                                                    </Chip>
                                                </Link>
                                            )
                                            : <Tooltip>
                                                <Tooltip.Trigger>
                                                    <TriangleDashedIcon className="fill-muted h-6 bottom-0"/>
                                                </Tooltip.Trigger>
                                                <Tooltip.Content placement="right">Missing data</Tooltip.Content>
                                            </Tooltip>
                                        }
                                    </td>
                                </tr>
                                <tr>
                                    <td className="text-muted w-0 min-w-32">Themes</td>
                                    <td className="flex flex-row gap-1">
                                        {game.themes && game.themes.length > 0
                                            ? [...game.themes].sort().map((theme) =>
                                                <Link key={theme} href={`/search?theme=${encodeURIComponent(theme)}`}>
                                                    <Chip size="sm" className="text-sm">
                                                        {theme}
                                                    </Chip>
                                                </Link>
                                            )
                                            : <Tooltip>
                                                <Tooltip.Trigger>
                                                    <TriangleDashedIcon className="fill-muted h-6 bottom-0"/>
                                                </Tooltip.Trigger>
                                                <Tooltip.Content placement="right">Missing data</Tooltip.Content>
                                            </Tooltip>
                                        }
                                    </td>
                                </tr>
                                <tr>
                                    <td className="text-muted w-0 min-w-32">Features</td>
                                    <td className="flex flex-row gap-1">
                                        {game.features && game.features.length > 0
                                            ? [...game.features].sort().map((feature) =>
                                                <Link key={feature} href={`/search?feature=${encodeURIComponent(feature)}`}>
                                                    <Chip size="sm" className="text-sm">
                                                        {feature}
                                                    </Chip>
                                                </Link>
                                            )
                                            : <Tooltip>
                                                <Tooltip.Trigger>
                                                    <TriangleDashedIcon className="fill-muted h-6 bottom-0"/>
                                                </Tooltip.Trigger>
                                                <Tooltip.Content placement="right">Missing data</Tooltip.Content>
                                            </Tooltip>
                                        }
                                    </td>
                                </tr>
                                {game.collectionIds.length > 0 &&
                                    <tr>
                                        <td className="text-muted w-0 min-w-32">Collections</td>
                                        <td className="flex flex-row gap-1">
                                            {[...game.collectionIds]
                                                .map((collectionId) => collections[collectionId])
                                                .sort((a, b) => a.id - b.id)
                                                .map((collection, index) =>
                                                    <React.Fragment key={collection.id}>
                                                        <Link href={`/collection/${collection.id}`} className="text-foreground hover:underline">
                                                            {collection.name}
                                                        </Link>
                                                        {index !== game.collectionIds!.length - 1 && <p>/</p>}
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
                            imageUrls={game.images?.map(image => `/images/screenshot/${image.id}`)}
                            videosUrls={game.videoUrls}
                            className="-mx-24"
                        />
                    </div>
                </div>
            </div>
            {isAdmin(auth) && <>
                <EditGameMetadataModal game={game}
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
