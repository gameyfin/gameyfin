import React, {useEffect, useState} from "react";
import {DownloadProviderEndpoint, GameEndpoint} from "Frontend/generated/endpoints";
import {useNavigate, useParams} from "react-router";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import ComboButton, {ComboButtonOption} from "Frontend/components/general/input/ComboButton";
import ImageCarousel from "Frontend/components/general/covers/ImageCarousel";
import {Accordion, AccordionItem, addToast, Button, Chip, Link, Tooltip, useDisclosure} from "@heroui/react";
import {humanFileSize, isAdmin, toTitleCase} from "Frontend/util/utils";
import {DownloadEndpoint} from "Frontend/endpoints/endpoints";
import {gameState, initializeGameState} from "Frontend/state/GameState";
import {useSnapshot} from "valtio/react";
import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {CheckCircle, Info, MagnifyingGlass, Pencil, Trash, TriangleDashed} from "@phosphor-icons/react";
import {useAuth} from "Frontend/util/auth";
import MatchGameModal from "Frontend/components/general/modals/MatchGameModal";
import EditGameMetadataModal from "Frontend/components/general/modals/EditGameMetadataModal";
import GameUpdateDto from "Frontend/generated/org/gameyfin/app/games/dto/GameUpdateDto";
import Markdown from "react-markdown";
import remarkBreaks from "remark-breaks";

export default function GameView() {
    const {gameId} = useParams();

    const navigate = useNavigate();
    const auth = useAuth();

    const editGameModal = useDisclosure();
    const matchGameModal = useDisclosure();

    const state = useSnapshot(gameState);
    const game = gameId ? state.state[parseInt(gameId)] as GameDto : undefined;

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
    }, []);

    useEffect(() => {
        initializeGameState().then((state) => {
            if (!gameId || !state.state[parseInt(gameId)]) {
                navigate("/", {replace: true});
            }
        });
    }, [gameId]);

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
                <div className="absolute inset-0 bg-gradient-to-b from-transparent to-background"/>
            </div>
            <div className="flex flex-col gap-4 mx-24">
                <div className="flex flex-row justify-between">
                    <div className="flex flex-row gap-4">
                        <div className="mt-[-16.25rem]">
                            <GameCover game={game} size={320} radius="none"/>
                        </div>
                        <div className="flex flex-col gap-1">
                            <p className="font-semibold text-3xl">{game.title}</p>
                            <div className="flex flex-row items-center gap-2">
                                <p className="text-default-500">
                                    {game.release !== undefined ? new Date(game.release).getFullYear() :
                                        <p className="text-default-500">no data</p>}
                                </p>
                                <Tooltip content={`Last update: ${new Date(game.updatedAt).toLocaleString()}`}
                                         placement="right">
                                    <Info/>
                                </Tooltip>
                            </div>
                        </div>
                    </div>
                    <div className="flex flex-row items-center gap-8">
                        {isAdmin(auth) && <div className="flex flex-row gap-2">
                            <Button isIconOnly onPress={toggleMatchConfirmed}>
                                {game.metadata.matchConfirmed ?
                                    <Tooltip content="Unconfirm match">
                                        <CheckCircle weight="fill" className="fill-success"/>
                                    </Tooltip> :
                                    <Tooltip content="Confirm match">
                                        <CheckCircle/>
                                    </Tooltip>}
                            </Button>
                            <Tooltip content="Edit metadata">
                                <Button isIconOnly onPress={editGameModal.onOpenChange}>
                                    <Pencil/>
                                </Button>
                            </Tooltip>
                            <Tooltip content="Search for metadata">
                                <Button isIconOnly onPress={matchGameModal.onOpenChange}>
                                    <MagnifyingGlass/>
                                </Button>
                            </Tooltip>
                            <Tooltip content="Remove from library">
                                <Button isIconOnly color="danger"
                                        onPress={async () => {
                                            await deleteGame();
                                            navigate("/");
                                        }}>
                                    <Trash/>
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
                                           startContent={<Info weight="fill"/>}>
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
                        <div className="flex flex-col flex-1 gap-2">
                            <p className="text-default-500">Details</p>
                            <table className="text-left w-full table-auto">
                                <tbody>
                                <tr className="h-6">
                                    <td className="text-default-500 w-0 min-w-32">Developed by</td>
                                    <td className="flex flex-row gap-1">
                                        {game.developers && game.developers.length > 0
                                            ? [...game.developers].sort().map((dev, index) =>
                                                <>
                                                    <Link key={dev} href={`/search?dev=${encodeURIComponent(dev)}`}
                                                          color="foreground" underline="hover">
                                                        {dev}
                                                    </Link>
                                                    {index !== game.developers!!.length - 1 && <p>/</p>}
                                                </>
                                            )
                                            : <Tooltip content="Missing data" color="foreground" placement="right">
                                                <TriangleDashed className="fill-default-500 h-6 bottom-0"/>
                                            </Tooltip>
                                        }
                                    </td>
                                </tr>
                                <tr className="h-6">
                                    <td className="text-default-500 w-0 min-w-32">Published by</td>
                                    <td className="flex flex-row gap-1">
                                        {game.publishers && game.publishers.length > 0
                                            ? [...game.publishers].sort().join(" / ")
                                            : <Tooltip content="Missing data" color="foreground" placement="right">
                                                <TriangleDashed className="fill-default-500 h-6 bottom-0"/>
                                            </Tooltip>
                                        }
                                    </td>
                                </tr>
                                <tr className="h-6">
                                    <td className="text-default-500 w-0 min-w-32">Genres</td>
                                    <td className="flex flex-row gap-1">
                                        {game.genres && game.genres.length > 0
                                            ? [...game.genres].sort().map(genre =>
                                                <Link key={genre} href={`/search?genre=${encodeURIComponent(genre)}`}>
                                                    <Chip radius="sm">{toTitleCase(genre)}</Chip>
                                                </Link>
                                            )
                                            : <Tooltip content="Missing data" color="foreground" placement="right">
                                                <TriangleDashed className="fill-default-500 h-6 bottom-0"/>
                                            </Tooltip>
                                        }
                                    </td>
                                </tr>
                                <tr className="h-6">
                                    <td className="text-default-500 w-0 min-w-32">Themes</td>
                                    <td className="flex flex-row gap-1">
                                        {game.themes && game.themes.length > 0
                                            ? [...game.themes].sort().map(theme =>
                                                <Link key={theme} href={`/search?theme=${encodeURIComponent(theme)}`}>
                                                    <Chip radius="sm">{toTitleCase(theme)}</Chip>
                                                </Link>
                                            )
                                            : <Tooltip content="Missing data" color="foreground" placement="right">
                                                <TriangleDashed className="fill-default-500 h-6 bottom-0"/>
                                            </Tooltip>
                                        }
                                    </td>
                                </tr>
                                <tr className="h-6">
                                    <td className="text-default-500 w-0 min-w-32">Features</td>
                                    <td className="flex flex-row gap-1">
                                        {game.features && game.features.length > 0
                                            ? [...game.features].sort().map(feature =>
                                                <Link key={feature}
                                                      href={`/search?feature=${encodeURIComponent(feature)}`}>
                                                    <Chip radius="sm">{toTitleCase(feature)}</Chip>
                                                </Link>
                                            )
                                            : <Tooltip content="Missing data" color="foreground" placement="right">
                                                <TriangleDashed className="fill-default-500 h-6 bottom-0"/>
                                            </Tooltip>
                                        }
                                    </td>
                                </tr>
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
            <MatchGameModal path={game.metadata.path!!}
                            libraryId={game.libraryId}
                            replaceGameId={game.id}
                            initialSearchTerm={game.title}
                            isOpen={matchGameModal.isOpen}
                            onOpenChange={matchGameModal.onOpenChange}/>
        </div>
    );
}