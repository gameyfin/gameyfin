import {useEffect, useState} from "react";
import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import {GameEndpoint} from "Frontend/generated/endpoints";
import {useParams} from "react-router";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import ComboButton, {ComboButtonOption} from "Frontend/components/general/input/ComboButton";

export default function GameView() {
    const {gameId} = useParams();

    const [game, setGame] = useState<GameDto>();
    const [selectedDownloadOption, setSelectedDownloadOption] = useState<string | null>(null);

    const downloadOptions: Record<string, ComboButtonOption> = {
        browser: {
            label: "Direct Download",
            description: "Download the game in this browser",
            action: () => {
                alert("Direct download not yet implemented")
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
                <div className="overflow-hidden rounded-lg">
                    <img className="w-full h-96 object-cover brightness-50 blur-sm scale-110" alt="Game screenshot"
                         src={`/images/screenshot/${game.imageIds[0]}`}/>
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
                    <ComboButton options={downloadOptions} preferredOptionKey="preferred-download-method"/>
                </div>
                <div className="flex flex-col gap-2">
                    <p className="text-foreground/60">Summary</p>
                    <p>{game.summary}</p>
                </div>
            </div>
        </div>
    ));
}