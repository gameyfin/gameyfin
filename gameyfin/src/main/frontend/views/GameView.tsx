import {useEffect, useState} from "react";
import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import {GameEndpoint} from "Frontend/generated/endpoints";
import {useParams} from "react-router";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import ComboButton, {ComboButtonOption} from "Frontend/components/general/input/ComboButton";
import ImageCarousel from "Frontend/components/general/covers/ImageCarousel";
import {Chip} from "@heroui/react";
import {humanFileSize, toTitleCase} from "Frontend/util/utils";
import {DownloadEndpoint} from "Frontend/endpoints/endpoints";

export default function GameView() {
    const {gameId} = useParams();

    const [game, setGame] = useState<GameDto>();

    const downloadOptions: Record<string, ComboButtonOption> = {
        browser: {
            label: "Direct Download",
            description: "Download the game in this browser",
            action: () => {
                DownloadEndpoint.downloadGame(parseInt(gameId!), "de.grimsi.gameyfin.plugins.directdownload.DirectDownloadPlugin$DirectDownloadProvider")
            }
        },
        torrent: {
            label: "Torrent Download",
            description: "Download the game as a torrent",
            action: () => {
                alert("Torrent download not yet implemented")
            },
            isDisabled: true
        }
    }

    useEffect(() => {
        if (gameId) {
            GameEndpoint.getGame(parseInt(gameId)).then((game) => setGame(game));
        }
    }, [gameId]);

    return (game && (
        <div className="flex flex-col gap-4">
            {game.imageIds !== undefined && game.imageIds.length > 0 &&
                <div className="overflow-hidden rounded-lg relative">
                    <img className="w-full h-96 object-cover brightness-50 blur-sm scale-110"
                         alt="Game screenshot"
                         src={`/images/screenshot/${game.imageIds[0]}`}
                    />
                    <div
                        className="absolute inset-0 pointer-events-none bg-gradient-to-b from-transparent to-background"/>
                </div>
            }
            <div className="flex flex-col gap-4 mx-24">
                <div className="flex flex-row justify-between">
                    <div className="flex flex-row gap-4">
                        <div className="mt-[-16.25rem]">
                            <GameCover game={game} size={320} radius="none"/>
                        </div>
                        <div className="flex flex-col gap-1">
                            <p className="font-semibold text-3xl">{game.title}</p>
                            <p className="text-foreground/60">{game.release !== undefined ? new Date(game.release).getFullYear() : "unknown"}</p>
                        </div>
                    </div>
                    <ComboButton description={humanFileSize(game.fileSize)}
                                 options={downloadOptions}
                                 preferredOptionKey="preferred-download-method"
                    />
                </div>
                <div className="flex flex-col gap-8">
                    <div className="flex flex-row gap-12">
                        <div className="flex flex-col flex-1 gap-2">
                            <p className="text-foreground/60">Summary</p>
                            {game.summary ?
                                <div className="text-justify" dangerouslySetInnerHTML={{__html: game.summary}}/> :
                                <p>No summary available</p>
                            }
                        </div>
                        <div className="flex flex-col flex-1 gap-2">
                            <p className="text-foreground/60">Details</p>
                            <table className="text-left w-full table-auto">
                                <tbody>
                                {Object.entries({
                                    "Developed by": game.developers?.sort().join(" / "),
                                    "Published by": game.publishers?.sort().join(" / "),
                                    "Genres": game.genres?.sort().map(p => <Chip radius="sm">{toTitleCase(p)}</Chip>),
                                    "Themes": game.themes?.sort().map(p => <Chip radius="sm">{toTitleCase(p)}</Chip>),
                                    "Features": game.features?.sort().map(p => <Chip
                                        radius="sm">{toTitleCase(p)}</Chip>),
                                }).map(([key, value]) => (
                                    <tr key={key}>
                                        <td className="text-foreground/60 w-0 min-w-32">{key}</td>
                                        <td className="flex flex-row gap-1">{value}</td>
                                    </tr>
                                ))}
                                </tbody>
                            </table>
                        </div>
                    </div>
                    <div className="flex flex-col gap-4">
                        <p className="text-foreground/60">Media</p>
                        <ImageCarousel
                            imageUrls={game.imageIds?.map(id => `/images/screenshot/${id}`)}
                            videosUrls={game.videoUrls}
                            className="-mx-24"
                        />
                    </div>
                </div>
            </div>
        </div>
    ));
}