import React, {useEffect, useState} from "react";
import {DownloadProviderEndpoint, GameEndpoint} from "Frontend/generated/endpoints";
import {useNavigate, useParams} from "react-router";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import ComboButton, {ComboButtonOption} from "Frontend/components/general/input/ComboButton";
import ImageCarousel from "Frontend/components/general/covers/ImageCarousel";
import {Accordion, AccordionItem, addToast, Button, Chip, Link, Tooltip, useDisclosure} from "@heroui/react";
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
    TriangleDashedIcon
} from "@phosphor-icons/react";
import {useAuth} from "Frontend/util/auth";
import MatchGameModal from "Frontend/components/general/modals/MatchGameModal";
import EditGameMetadataModal from "Frontend/components/general/modals/EditGameMetadataModal";
import GameUpdateDto from "Frontend/generated/org/gameyfin/app/games/dto/GameUpdateDto";
import Markdown from "react-markdown";
import remarkBreaks from "remark-breaks";
import {GameAdminDto} from "Frontend/dtos/GameDtos";
import ChipList from "Frontend/components/general/ChipList";
import {collectionState} from "Frontend/state/CollectionState";
import CollectionDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionDto";

export default function GameView() {
    const {gameId} = useParams();

    const navigate = useNavigate();
    const auth = useAuth();

    const editGameModal = useDisclosure();
    const matchGameModal = useDisclosure();

    const state = useSnapshot(gameState);
    const game = gameId ? state.state[parseInt(gameId)] as GameAdminDto : undefined;
    const collections = useSnapshot(collectionState).state as Record<number, CollectionDto>;

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
                metadata: {matchConfirmed: !game.metadata.matchConfirmed}
            } as GameUpdateDto
        )
    }

    async function deleteGame() {
        if (!game) return;
        await GameEndpoint.deleteGame(game.id);
        addToast({
            title: "Game deleted",
            description: `${game.title} removed from Gameyfin!`,
            color: "success"
        });
    }

    return game && (
        <div className="flex flex-col gap-4">
            <div className="overflow-hidden relative rounded-t-lg">
                {game.headerId ? (
                    <img
                        className="w-full h-96 object-cover brightness-50 blur-sm scale-110"
                        alt="Game header"
                        src={`/images/header/${game.headerId}`}
                    />
                ) : game.imageIds && game.imageIds.length > 0 ? (
                    <img
                        className="w-full h-96 object-cover brightness-50 blur-sm scale-110"
                        alt="Game screenshot"
                        src={`/images/screenshot/${game.imageIds[0]}`}
                    />
                ) : (
                    <div className="w-full h-96 bg-secondary relative"/>
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
                                <div className="flex flex-row gap-1 mb-0.5 text-default-500">
                                    <StarIcon weight="fill"/>
                                    {starRatingAsString(game)}
                                </div>
                            </div>
                            <div className="flex flex-row items-center gap-2">
                                <p className="text-default-500">
                                    {game.release !== undefined ? new Date(game.release).getFullYear() :
                                        <p className="text-default-500">no data</p>}
                                </p>
                                <ChipList items={game.platforms} maxVisible={1}/>
                                <Tooltip
                                    content={`Last update: ${new Date(game.updatedAt).toLocaleString()}`}
                                    placement="right">
                                    <InfoIcon/>
                                </Tooltip>
                            </div>
                        </div>
                    </div>
                    <div className="flex flex-row items-center gap-8">
                        {isAdmin(auth) && <div className="flex flex-row gap-2">
                            <Button isIconOnly onPress={toggleMatchConfirmed}>
                                {game.metadata.matchConfirmed ?
                                    <Tooltip content="Unconfirm match">
                                        <CheckCircleIcon weight="fill" className="fill-success"/>
                                    </Tooltip> :
                                    <Tooltip content="Confirm match">
                                        <CheckCircleIcon/>
                                    </Tooltip>}
                            </Button>
                            <Tooltip content="Edit metadata">
                                <Button isIconOnly onPress={editGameModal.onOpenChange}>
                                    <PencilIcon/>
                                </Button>
                            </Tooltip>
                            <Tooltip content="Search for metadata">
                                <Button isIconOnly onPress={matchGameModal.onOpenChange}>
                                    <MagnifyingGlassIcon/>
                                </Button>
                            </Tooltip>
                            <Tooltip content="Remove from library">
                                <Button isIconOnly color="danger"
                                        onPress={async () => {
                                            await deleteGame();
                                            navigate("/");
                                        }}>
                                    <TrashIcon/>
                                </Button>
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
                        <Accordion variant="splitted"
                                   itemClasses={{base: "-mx-2", content: "mx-8 mb-4", heading: "font-bold"}}>
                            <AccordionItem key="information"
                                           aria-label="Information"
                                           title="Information"
                                           startContent={<InfoIcon weight="fill"/>}>
                                <Markdown
                                    remarkPlugins={[remarkBreaks]}
                                    components={{
                                        a(props) {
                                            return <Link isExternal
                                                         showAnchorIcon
                                                         color="foreground"
                                                         underline="always"
                                                         href={props.href}
                                                         size="sm">
                                                {props.children}
                                            </Link>
                                        }
                                    }}
                                >{game.comment}</Markdown>
                            </AccordionItem>
                        </Accordion>
                    }
                    <div className="flex flex-row gap-12">
                        <div className="flex flex-col flex-1 gap-2">
                            <p className="text-default-500">Summary</p>
                            {game.summary ?
                                <div className="text-justify" dangerouslySetInnerHTML={{__html: game.summary}}/> :
                                <p>No summary available</p>
                            }
                        </div>
                        <div className="flex flex-col flex-1">
                            <p className="text-default-500">Details</p>
                            <table
                                className="text-left w-full table-auto border-separate border-spacing-y-1">
                                <tbody>
                                <tr>
                                    <td className="text-default-500 w-0 min-w-32">Developed by</td>
                                    <td className="flex flex-row gap-1">
                                        {game.developers && game.developers.length > 0
                                            ? [...game.developers].sort().map((dev, index) =>
                                                <>
                                                    <Link key={dev} href={`/search?dev=${encodeURIComponent(dev)}`}
                                                          color="foreground" underline="hover">
                                                        {dev}
                                                    </Link>
                                                    {index !== game.developers!.length - 1 && <p>/</p>}
                                                </>
                                            )
                                            : <Tooltip content="Missing data" color="foreground" placement="right">
                                                <TriangleDashedIcon className="fill-default-500 h-6 bottom-0"/>
                                            </Tooltip>
                                        }
                                    </td>
                                </tr>
                                <tr>
                                    <td className="text-default-500 w-0 min-w-32">Published by</td>
                                    <td className="flex flex-row gap-1">
                                        {game.publishers && game.publishers.length > 0
                                            ? [...game.publishers].sort().join(" / ")
                                            : <Tooltip content="Missing data" color="foreground" placement="right">
                                                <TriangleDashedIcon className="fill-default-500 h-6 bottom-0"/>
                                            </Tooltip>
                                        }
                                    </td>
                                </tr>
                                <tr>
                                    <td className="text-default-500 w-0 min-w-32">Genres</td>
                                    <td className="flex flex-row gap-1">
                                        {game.genres && game.genres.length > 0
                                            ? [...game.genres].sort().map(genre =>
                                                <Link key={genre} href={`/search?genre=${encodeURIComponent(genre)}`}>
                                                    <Chip radius="sm" size="sm"
                                                          className="text-sm">
                                                        {genre}
                                                    </Chip>
                                                </Link>
                                            )
                                            : <Tooltip content="Missing data" color="foreground" placement="right">
                                                <TriangleDashedIcon className="fill-default-500 h-6 bottom-0"/>
                                            </Tooltip>
                                        }
                                    </td>
                                </tr>
                                <tr>
                                    <td className="text-default-500 w-0 min-w-32">Themes</td>
                                    <td className="flex flex-row gap-1">
                                        {game.themes && game.themes.length > 0
                                            ? [...game.themes].sort().map(theme =>
                                                <Link key={theme} href={`/search?theme=${encodeURIComponent(theme)}`}>
                                                    <Chip radius="sm" size="sm"
                                                          className="text-sm">
                                                        {theme}
                                                    </Chip>
                                                </Link>
                                            )
                                            : <Tooltip content="Missing data" color="foreground" placement="right">
                                                <TriangleDashedIcon className="fill-default-500 h-6 bottom-0"/>
                                            </Tooltip>
                                        }
                                    </td>
                                </tr>
                                <tr>
                                    <td className="text-default-500 w-0 min-w-32">Features</td>
                                    <td className="flex flex-row gap-1">
                                        {game.features && game.features.length > 0
                                            ? [...game.features].sort().map(feature =>
                                                <Link key={feature}
                                                      href={`/search?feature=${encodeURIComponent(feature)}`}>
                                                    <Chip radius="sm" size="sm"
                                                          className="text-sm">
                                                        {feature}
                                                    </Chip>
                                                </Link>
                                            )
                                            : <Tooltip content="Missing data" color="foreground" placement="right">
                                                <TriangleDashedIcon className="fill-default-500 h-6 bottom-0"/>
                                            </Tooltip>
                                        }
                                    </td>
                                </tr>
                                {game.collectionIds.length > 0 &&
                                    <tr>
                                        <td className="text-default-500 w-0 min-w-32">Collections</td>
                                        <td className="flex flex-row gap-1">
                                            {[...game.collectionIds]
                                                .map((collectionId) => collections[collectionId])
                                                .sort((a, b) => a.id - b.id)
                                                .map((collection, index) =>
                                                    <>
                                                        <Link key={collection.id} href={`/collection/${collection.id}`}
                                                              color="foreground" underline="hover">
                                                            {collection.name}
                                                        </Link>
                                                        {index !== game.collectionIds!.length - 1 && <p>/</p>}
                                                    </>
                                                )}
                                        </td>
                                    </tr>
                                }
                                </tbody>
                            </table>
                        </div>
                    </div>
                    <div className="flex flex-col gap-4">
                        <p className="text-default-500">Media</p>
                        <ImageCarousel
                            imageUrls={game.imageIds?.map(id => `/images/screenshot/${id}`)}
                            videosUrls={game.videoUrls}
                            className="-mx-24"
                        />
                    </div>
                </div>
            </div>
            <EditGameMetadataModal game={game}
                                   isOpen={editGameModal.isOpen}
                                   onOpenChange={editGameModal.onOpenChange}/>
            <MatchGameModal path={game.metadata.path!}
                            libraryId={game.libraryId}
                            replaceGameId={game.id}
                            initialSearchTerm={game.title}
                            isOpen={matchGameModal.isOpen}
                            onOpenChange={matchGameModal.onOpenChange}/>
        </div>
    );
}