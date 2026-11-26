import {Card, Checkbox} from "@heroui/react";
import React, {useMemo} from "react";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import CollectionDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionDto";
import {Link} from "react-router";
import {useCollectionStats, useRandomlyOrderedGamesByCollectionId} from "Frontend/state/derived";
import IconBackgroundPattern from "Frontend/components/general/IconBackgroundPattern";
import ChipList from "Frontend/components/general/ChipList";

interface CollectionOverviewCardProps {
    collection: CollectionDto;
    isSelected?: boolean;
    onToggleSelection?: () => void;
    href?: string;
}

export function CollectionOverviewCard({collection, isSelected, onToggleSelection, href = `/collection/${collection.id}`}: CollectionOverviewCardProps) {
    const MAX_COVER_COUNT = 5;
    const randomlyOrderedGames = useRandomlyOrderedGamesByCollectionId()[collection.id];
    const randomGames = useMemo(() => (randomlyOrderedGames ?? [])
        .filter(game => game.cover?.id != null)
        .slice(0, MAX_COVER_COUNT), [randomlyOrderedGames]);
    const stats = useCollectionStats(collection.id);


    return (
        <li className="relative isolate max-w-88.25">
            {onToggleSelection && <Checkbox aria-label={`Select ${collection.name}`} isSelected={isSelected} onChange={onToggleSelection}
                      className="absolute top-2 left-2 z-20">
                <Checkbox.Content>
                    <Checkbox.Control className={isSelected ? undefined : "border-2 border-foreground bg-surface"}>
                        <Checkbox.Indicator/>
                    </Checkbox.Control>
                </Checkbox.Content>
            </Checkbox>}
            <Link to={href} aria-label={`Open ${collection.name}`}
                  className="absolute inset-0 z-10 rounded-xl focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent"/>
            <Card data-selected={isSelected || undefined}
                  className="flex flex-col justify-between w-full max-w-88.25 p-0 overflow-hidden data-selected:ring-2 data-selected:ring-accent">
                <div className="flex flex-1 justify-center items-center">
                    <div className="flex flex-1 opacity-10 min-h-25">
                        <IconBackgroundPattern/>
                        {randomGames.length > 0 &&
                            <div className="absolute flex flex-row">
                                {randomGames.map((game) => (
                                    <GameCover game={game} size={100} radius="none" key={game.cover?.id}/>
                                ))}
                            </div>
                        }
                    </div>

                    <div className="absolute max-w-full px-3 text-center wrap-anywhere">
                        <p className="text-2xl font-bold">{collection.name}</p>
                        {collection.ownerName && <p className="text-muted">{collection.ownerName}</p>}
                    </div>
                </div>

                {stats &&
                    <div
                        className="relative grid grid-rows-2 grid-cols-3 justify-items-center items-center p-2 pt-4 [&_button]:relative [&_button]:z-20">
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
        </li>
    );
}
