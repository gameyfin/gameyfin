import {Button, Card, Chip, Tooltip} from "@heroui/react";
import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/LibraryDto";
import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import React, {useEffect, useState} from "react";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import {GameCover} from "Frontend/components/general/GameCover";
import Rand from "rand-seed";
import {
    Alien,
    CastleTurret,
    GameController,
    Ghost,
    Joystick,
    Lego,
    MagnifyingGlass,
    Skull,
    SoccerBall,
    Strategy,
    Sword,
    TreasureChest,
    Trophy
} from "@phosphor-icons/react";

export function LibraryOverviewCard({library}: { library: LibraryDto }) {
    const MAX_COVER_COUNT = 5;
    const rand = new Rand(library.id.toString());

    const [randomGamesFromLibrary, setRandomGamesFromLibrary] = useState<GameDto[]>([]);

    useEffect(() => {
        LibraryEndpoint.getGamesInLibrary(library.id).then(
            (response) => {
                if (response === undefined) return;
                const count = Math.min(response.length, MAX_COVER_COUNT)

                let gamesFromLibrary: GameDto[] = response
                    .filter(g => !!g)
                    .sort(() => rand.next() - 0.5)
                    .slice(0, count)

                setRandomGamesFromLibrary(gamesFromLibrary);
            }
        )
    }, []);

    return (
        <Card className="flex flex-col justify-between w-[353px]">
            <div className="flex flex-1 justify-center items-center">
                <div className="flex flex-1 opacity-10 min-h-[100px]">
                    <div className="absolute w-full h-full opacity-50">
                        <GameController size={32}
                                        className="absolute fill-primary top-[10%] left-[10%] rotate-[350deg]"/>
                        <SoccerBall size={34}
                                    className="absolute fill-primary top-[50%] left-[35%] rotate-[60deg]"/>
                        <Joystick size={40} className="absolute top-[30%] left-[50%] rotate-[90deg]"/>
                        <Strategy size={36} className="absolute fill-primary top-[50%] left-[70%] rotate-[30deg]"/>
                        <Sword size={28} className="absolute top-[70%] left-[10%] rotate-[60deg]"/>
                        <Alien size={34} className="absolute fill-primary top-[10%] left-[85%] rotate-[15deg]"/>
                        <CastleTurret size={30} className="absolute top-[5%] left-[40%] rotate-[320deg]"/>
                        <Ghost size={38} className="absolute fill-primary top-[40%] left-[5%] rotate-[300deg]"/>
                        <Skull size={32} className="absolute top-[80%] left-[30%] rotate-[90deg]"/>
                        <Trophy size={36} className="absolute fill-primary top-[10%] left-[60%] rotate-[45deg]"/>
                        <Lego size={28} className="absolute top-[30%] left-[20%] rotate-[30deg]"/>
                        <TreasureChest size={40} className="absolute top-[70%] left-[50%] rotate-[75deg]"/>
                    </div>
                    {randomGamesFromLibrary.length > 0 &&
                        <div className="absolute flex flex-row">
                            {randomGamesFromLibrary.map((game) => (
                                <GameCover game={game} size={100} radius="none" key={game.coverId}/>
                            ))}
                        </div>
                    }
                </div>

                <p className="absolute text-2xl font-bold">{library.name}</p>

                <div className="absolute right-0 top-0 flex flex-row">
                    <Tooltip content="Scan library" placement="bottom" color="foreground">
                        <Button isIconOnly variant="light" onPress={() => LibraryEndpoint.triggerScan([library])}>
                            <MagnifyingGlass/>
                        </Button>
                    </Tooltip>
                </div>
            </div>

            {!!library.stats &&
                <div className="grid grid-rows-2 grid-cols-3 justify-items-center items-center p-2 pt-4">
                    <p>Games</p>
                    <p>Downloads</p>
                    <p>Platforms</p>
                    <p className="font-bold">{library.stats.gamesCount}</p>
                    <p className="font-bold">{library.stats.downloadedGamesCount}</p>
                    <Chip size="sm">PC</Chip>
                </div>
            }
        </Card>
    );
}