import {Card, Checkbox} from "@heroui/react";
import {GlobeIcon, LockSimpleIcon, SparkleIcon, UsersIcon} from "@phosphor-icons/react";
import React, {useMemo} from "react";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import CollectionDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionDto";
import CollectionVisibility from "Frontend/generated/org/gameyfin/app/collections/entities/CollectionVisibility";
import {Link} from "react-router";
import {useCollectionStats, useRandomlyOrderedGamesByCollectionId} from "Frontend/state/derived";
import IconBackgroundPattern from "Frontend/components/general/IconBackgroundPattern";
import ChipList from "Frontend/components/general/ChipList";

const visibilityIndicators = {
    [CollectionVisibility.PRIVATE]: {label: "Private", icon: LockSimpleIcon},
    [CollectionVisibility.MEMBERS_ONLY]: {label: "Members only", icon: UsersIcon},
    [CollectionVisibility.PUBLIC]: {label: "Public", icon: GlobeIcon}
};

interface CollectionOverviewCardProps {
    collection: CollectionDto;
    isSelected?: boolean;
    onToggleSelection?: () => void;
    management?: boolean;
}

export function CollectionOverviewCard({
                                           collection, isSelected, onToggleSelection, management = false
                                       }: CollectionOverviewCardProps) {
    const href = management ? `/administration/games/collection/${collection.id}` : `/collection/${collection.id}`;
    const MAX_COVER_COUNT = 5;
    const randomlyOrderedGames = useRandomlyOrderedGamesByCollectionId()[collection.id];
    const randomGames = useMemo(() => (randomlyOrderedGames ?? [])
        .filter(game => game.cover?.id != null)
        .slice(0, MAX_COVER_COUNT), [randomlyOrderedGames]);
    const stats = useCollectionStats(collection.id);
    const {label: visibilityLabel, icon: VisibilityIcon} = visibilityIndicators[collection.visibility];
    const description = `Visibility: ${visibilityLabel}${collection.smartEnabled ? ". Smart Collection" : ""}`;

    return (
        <li className="relative isolate max-w-88.25">
            {onToggleSelection &&
                <Checkbox aria-label={`Select ${collection.name}`} isSelected={isSelected} onChange={onToggleSelection}
                          className="absolute top-2 left-2 z-20">
                    <Checkbox.Content>
                        <Checkbox.Control className={isSelected ? undefined : "border-2 border-foreground bg-surface"}>
                            <Checkbox.Indicator/>
                        </Checkbox.Control>
                    </Checkbox.Content>
                </Checkbox>}
            <Link to={href} aria-label={`Open ${collection.name}`}
                  aria-description={description}
                  className="absolute inset-0 z-10 rounded-xl focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-accent">
                <span className="absolute inset-e-3 top-3 flex items-center gap-1 text-foreground" aria-hidden="true">
                    {collection.smartEnabled &&
                        <SparkleIcon data-slot="smart-collection-icon" weight="fill" className="size-5 text-accent"/>}
                    <span title={visibilityLabel}><VisibilityIcon className="size-5"/></span>
                </span>
            </Link>
            <Card data-selected={isSelected || undefined}
                  className="relative flex flex-col justify-between w-full max-w-88.25 p-0 overflow-hidden data-selected:ring-2 data-selected:ring-accent">
                <div className="pointer-events-none absolute inset-0 opacity-10" aria-hidden="true">
                    <IconBackgroundPattern/>
                </div>
                <div className="relative flex min-h-25 flex-1 items-center justify-center overflow-hidden">
                    {randomGames.length > 0 &&
                        <div className="pointer-events-none absolute inset-s-0 top-0 flex w-max opacity-10"
                             aria-hidden="true">
                            {randomGames.map((game) => (
                                <GameCover game={game} size={100} radius="none" key={game.cover?.id}/>
                            ))}
                        </div>}

                    <div
                        className={`relative max-w-full py-3 text-center wrap-anywhere ${collection.smartEnabled ? "px-15" : "px-9"}`}>
                        <p className="line-clamp-2 text-2xl font-bold">{collection.name}</p>
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
