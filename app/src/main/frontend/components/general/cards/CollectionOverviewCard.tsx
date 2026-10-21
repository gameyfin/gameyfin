import {Button, Card, Tooltip} from "@heroui/react";
import React, {useMemo} from "react";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import CollectionDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionDto";
import {SlidersHorizontalIcon} from "@phosphor-icons/react";
import {useNavigate} from "react-router";
import {useCollectionStats, useRandomlyOrderedGamesByCollectionId} from "Frontend/state/derived";
import IconBackgroundPattern from "Frontend/components/general/IconBackgroundPattern";
import ChipList from "Frontend/components/general/ChipList";

interface CollectionOverviewCardProps {
    collection: CollectionDto;
}

export function CollectionOverviewCard({collection}: CollectionOverviewCardProps) {
    const MAX_COVER_COUNT = 5;
    const navigate = useNavigate();
    const randomlyOrderedGames = useRandomlyOrderedGamesByCollectionId()[collection.id];
    const randomGames = useMemo(() => (randomlyOrderedGames ?? [])
        .filter(game => game.cover?.id != null)
        .slice(0, MAX_COVER_COUNT), [randomlyOrderedGames]);
    const stats = useCollectionStats(collection.id);


    return (
        <Card className="flex flex-col justify-between w-[353px] p-0 overflow-hidden">
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
                    <Tooltip delay={0}>
                        <Tooltip.Trigger>
                            <Button isIconOnly variant="ghost" onPress={() => navigate('collection/' + collection.id)}>
                                <SlidersHorizontalIcon/>
                            </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                            Configuration
                        </Tooltip.Content>
                    </Tooltip>
                </div>
            </div>

            {stats &&
                <div className="grid grid-rows-2 grid-cols-3 justify-items-center items-center p-2 pt-4 relative z-10">
                    <p>Games</p>
                    <p>Downloads</p>
                    <p>Platforms</p>
                    <p className="font-bold">{stats.gamesCount}</p>
                    <p className="font-bold">{stats.downloadCount}</p>
                    <ChipList items={stats.gamePlatforms} maxVisible={0}
                              defaultContent={stats.gamesCount > 0 ? "All" : "None"}/>
                </div>
            }
        </Card>
    );
}