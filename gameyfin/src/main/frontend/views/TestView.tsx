import {Link} from "react-router-dom";
import {addToast, Button, Input} from "@heroui/react";
import {LibraryEndpoint, SystemEndpoint} from "Frontend/generated/endpoints";
import {useState} from "react";
import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import {GameOverviewCard} from "Frontend/components/general/cards/GameOverviewCard";

export default function TestView() {
    const [gameTitle, setGameTitle] = useState("");
    const [game, setGame] = useState<GameDto>();

    function getGame() {
        LibraryEndpoint.test(gameTitle).then(game => {
            if (game == undefined) return;
            setGame(game);
        });
    }

    function removeGames() {
        LibraryEndpoint.removeGames().then(() => {
            setGame(undefined);
            addToast({
                title: "Success",
                description: "Games removed",
                color: "success"
            })
        });
    }

    return (
        <div className="grow justify-center mt-12">
            <div className="flex flex-col items-center gap-6">
                <Link to="/setup">Setup</Link>
                <div className="flex flex-row gap-4">
                    <Button onPress={
                        () => addToast({
                            title: "Primary",
                            description: "Description"
                        })
                    }>Toast (Normal)</Button>
                    <Button onPress={
                        () => addToast({
                            title: "Success",
                            description: "Description",
                            color: "success"
                        })
                    }>Toast (Success)</Button>
                    <Button onPress={
                        () => addToast({
                            title: "Error",
                            description: "Description",
                            color: "danger"
                        })
                    }>Toast (Error)</Button>
                </div>
                <Button onPress={() => SystemEndpoint.restart()}>Restart</Button>
                <div className="flex flex-row gap-4 items-center">
                    <Input label="Game title" onValueChange={setGameTitle}/>
                    <Button onPress={getGame} size="lg">Match</Button>
                    <Button onPress={removeGames} size="lg">Clear DB</Button>
                </div>
                {game && <GameOverviewCard game={game}></GameOverviewCard>}
                {game && <>{JSON.stringify(game, null, 2)}</>}
            </div>
        </div>
    );
}