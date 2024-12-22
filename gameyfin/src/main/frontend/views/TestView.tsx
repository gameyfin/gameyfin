import {Link} from "react-router-dom";
import {Button, Input} from "@nextui-org/react";
import {toast} from "sonner";
import {LibraryEndpoint, SystemEndpoint} from "Frontend/generated/endpoints";
import {useState} from "react";
import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";

export default function TestView() {
    const [gameTitle, setGameTitle] = useState("");
    const [game, setGame] = useState<GameDto>();

    function getGame() {
        LibraryEndpoint.test(gameTitle).then(game => {
            if (game == undefined) return;
            setGame(game);
        });
    }

    return (
        <div className="grow justify-center mt-12">
            <div className="flex flex-col items-center gap-6">
                <Link to="/setup">Setup</Link>
                <div className="flex flex-row gap-4">
                    <Button onPress={
                        () => toast("Normal", {
                            description: "Description",
                            action: {
                                label: "OK",
                                onClick: () => {
                                },
                            }
                        })}>Toast (Normal)</Button>
                    <Button onPress={
                        () => toast.success("Success", {
                            description: "Description",
                            action: {
                                label: "OK",
                                onClick: () => {
                                },
                            }
                        })}>Toast (Success)</Button>
                    <Button onPress={
                        () => toast.error("Error", {
                            description: "Description",
                            action: {
                                label: "OK",
                                onClick: () => {
                                },
                            }
                        })}>Toast (Error)</Button>
                </div>
                <Button onPress={() => SystemEndpoint.restart()}>Restart</Button>
                <div className="flex flex-row gap-4 items-center">
                    <Input label="Game title" onValueChange={setGameTitle}/>
                    <Button onPress={getGame} size="lg">Match</Button>
                </div>
                {game && <>{JSON.stringify(game, null, 2)}</>}
            </div>
        </div>
    );
}