import {Button, Card, Tooltip} from "@heroui/react";
import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import React, {useEffect, useState} from "react";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import CollectionAdminDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionAdminDto";
import {SlidersHorizontalIcon} from "@phosphor-icons/react";
import {useNavigate} from "react-router";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import IconBackgroundPattern from "Frontend/components/general/IconBackgroundPattern";
import ChipList from "Frontend/components/general/ChipList";

interface CollectionOverviewCardProps {
    collection: CollectionAdminDto;
}

export function CollectionOverviewCard({collection}: CollectionOverviewCardProps) {
    const MAX_COVER_COUNT = 5;
    const navigate = useNavigate();
    const state = useSnapshot(gameState);
    const [randomGames, setRandomGames] = useState<GameDto[]>([]);

    useEffect(() => {
        if (!state.randomlyOrderedGamesByCollectionId) return;
        setRandomGames(getRandomGames());
    }, [state]);

    function getRandomGames() {
        if (!state.randomlyOrderedGamesByCollectionId[collection.id]) return [];
        const games = state.randomlyOrderedGamesByCollectionId[collection.id]
            .filter(game => game.cover?.id != null);
        if (!games) return [];
        return games.slice(0, MAX_COVER_COUNT);
    }


    return (
        <Card className="flex flex-col justify-between w-[353px]">
            <div className="flex flex-1 justify-center items-center">
                <div className="flex flex-1 opacity-10 min-h-[100px]">
                    <IconBackgroundPattern/>
                    {randomGames.length > 0 &&
                        <div className="absolute flex flex-row">
                            {randomGames.map((game) => (
                                <GameCover game={game} size={100} radius="none" key={game.cover?.id}/>
                            ))}
                        </div>
                    }
                </div>

                <p className="absolute text-2xl font-bold">{collection.name}</p>

                <div className="absolute right-0 top-0 flex flex-row">
                    <Tooltip content="Configuration" placement="bottom" color="foreground">
                        <Button isIconOnly variant="light" onPress={() => navigate('collection/' + collection.id)}>
                            <SlidersHorizontalIcon/>
                        </Button>
                    </Tooltip>
                </div>
            </div>

            {collection.stats &&
                <div className="grid grid-rows-2 grid-cols-3 justify-items-center items-center p-2 pt-4">
                    <p>Games</p>
                    <p>Downloads</p>
                    <p>Platforms</p>
                    <p className="font-bold">{collection.stats.gamesCount}</p>
                    <p className="font-bold">{collection.stats.downloadCount}</p>
                    <ChipList items={collection.stats.gamePlatforms} maxVisible={0}
                              defaultContent={collection.stats.gamesCount > 0 ? "All" : "None"}/>
                </div>
            }
        </Card>
    );
}