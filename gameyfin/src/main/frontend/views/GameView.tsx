import {useEffect, useState} from "react";
import {DownloadProviderEndpoint} from "Frontend/generated/endpoints";
import {useNavigate, useParams} from "react-router";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import ComboButton, {ComboButtonOption} from "Frontend/components/general/input/ComboButton";
import ImageCarousel from "Frontend/components/general/covers/ImageCarousel";
import {Chip} from "@heroui/react";
import {humanFileSize, toTitleCase} from "Frontend/util/utils";
import {DownloadEndpoint} from "Frontend/endpoints/endpoints";
import {gameState, initializeGameState} from "Frontend/state/GameState";
import {useSnapshot} from "valtio/react";
import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";

export default function GameView() {
    const {gameId} = useParams();
    const navigate = useNavigate();
    const state = useSnapshot(gameState);
    const game = gameId ? state.state[parseInt(gameId)] as GameDto : undefined;

    const [downloadOptions, setDownloadOptions] = useState<Record<string, ComboButtonOption>>({});

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
                navigate("/");
            }
        });
    }, [gameId]);

    return game && (
        <div className="flex flex-col gap-4">
            <div className="overflow-hidden relative rounded-t-lg">
                {(game.imageIds && game.imageIds.length > 0) ?
                    <img className="w-full h-96 object-cover brightness-50 blur-sm scale-110"
                         alt="Game screenshot"
                         src={`/images/screenshot/${game.imageIds[0]}`}
                    /> :
                    <div className="w-full h-96 bg-secondary relative"/>
                }
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
                                    "Developed by": game.developers ? [...game.developers].sort().join(" / ") : "unknown",
                                    "Published by": game.publishers ? [...game.publishers].sort().join(" / ") : "unknown",
                                    "Genres": game.genres ? [...game.genres].sort().map(p =>
                                        <Chip radius="sm" key={p}>{toTitleCase(p)}</Chip>) : undefined,
                                    "Themes": game.themes ? [...game.themes].sort().map(p =>
                                        <Chip radius="sm" key={p}>{toTitleCase(p)}</Chip>) : undefined,
                                    "Features": game.features ? [...game.features].sort().map(p =>
                                        <Chip radius="sm" key={p}>{toTitleCase(p)}</Chip>) : undefined,
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
    );
}