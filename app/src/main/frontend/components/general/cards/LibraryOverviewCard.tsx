import {Button, Card, Chip, Tooltip} from "@heroui/react";
import LibraryDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryDto";
import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import React from "react";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import {MagnifyingGlass, SlidersHorizontal} from "@phosphor-icons/react";
import ScanType from "Frontend/generated/org/gameyfin/app/libraries/enums/ScanType";
import {useNavigate} from "react-router";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import IconBackgroundPattern from "Frontend/components/general/IconBackgroundPattern";

interface LibraryOverviewCardProps {
    library: LibraryDto;
}

export function LibraryOverviewCard({library}: LibraryOverviewCardProps) {
    const MAX_COVER_COUNT = 5;
    const navigate = useNavigate();
    const state = useSnapshot(gameState);
    const randomGames = getRandomGames();

    function getRandomGames() {
        const games = state.randomlyOrderedGamesByLibraryId[library.id] as GameDto[];
        if (!games) return [];
        return games.slice(0, MAX_COVER_COUNT);
    }

    async function triggerScan() {
        await LibraryEndpoint.triggerScan(ScanType.QUICK, [library]);
    }

    return (
        <Card className="flex flex-col justify-between w-[353px]">
            <div className="flex flex-1 justify-center items-center">
                <div className="flex flex-1 opacity-10 min-h-[100px]">
                    <IconBackgroundPattern/>
                    {randomGames.length > 0 &&
                        <div className="absolute flex flex-row">
                            {randomGames.map((game) => (
                                <GameCover game={game} size={100} radius="none" key={game.coverId}/>
                            ))}
                        </div>
                    }
                </div>

                <p className="absolute text-2xl font-bold">{library.name}</p>

                <div className="absolute right-0 top-0 flex flex-row">
                    <Tooltip content="Scan library" placement="bottom" color="foreground">
                        <Button isIconOnly variant="light" onPress={triggerScan}>
                            <MagnifyingGlass/>
                        </Button>
                    </Tooltip>
                    <Tooltip content="Configuration" placement="bottom" color="foreground">
                        <Button isIconOnly variant="light" onPress={() => navigate('library/' + library.id)}>
                            <SlidersHorizontal/>
                        </Button>
                    </Tooltip>
                </div>
            </div>

            {library.stats &&
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