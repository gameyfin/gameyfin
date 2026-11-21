import {Button, Card, Checkbox, Tooltip} from "@heroui/react";
import React from "react";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import {MagnifyingGlassIcon, MagnifyingGlassPlusIcon} from "@phosphor-icons/react";
import ScanType from "Frontend/generated/org/gameyfin/app/libraries/enums/ScanType";
import {Link} from "react-router";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import IconBackgroundPattern from "Frontend/components/general/IconBackgroundPattern";
import LibrarySummaryDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibrarySummaryDto";
import {useLibraryStats} from "Frontend/state/derived";
import ChipList from "Frontend/components/general/ChipList";
import {pluginState} from "Frontend/state/PluginState";

interface LibraryOverviewCardProps {
    library: LibrarySummaryDto;
    isSelected: boolean;
    onToggleSelection: () => void;
}

export function LibraryOverviewCard({library, isSelected, onToggleSelection}: LibraryOverviewCardProps) {
    const MAX_COVER_COUNT = 5;
    const state = useSnapshot(gameState);
    const randomGames = getRandomGames();
    const hasActiveMetadataPlugins = useSnapshot(pluginState).hasActiveMetadataPlugins;
    const stats = useLibraryStats(library.id);

    function getRandomGames() {
        if (!state.randomlyOrderedGamesByLibraryId[library.id]) return [];
        const games = state.randomlyOrderedGamesByLibraryId[library.id]
            .filter(game => game.cover?.id != null);
        if (!games) return [];
        return games.slice(0, MAX_COVER_COUNT);
    }

    async function triggerScan(scanType: ScanType) {
        await LibraryEndpoint.triggerScan(scanType, [library.id]);
    }

    return (
        <li className="relative isolate max-w-88.25">
            <Checkbox aria-label={`Select ${library.name}`} isSelected={isSelected} onChange={onToggleSelection}
                      className="absolute top-2 left-2 z-20">
                <Checkbox.Content>
                    <Checkbox.Control className={isSelected ? undefined : "border-2 border-foreground bg-surface"}>
                        <Checkbox.Indicator/>
                    </Checkbox.Control>
                </Checkbox.Content>
            </Checkbox>
            <Link to={`library/${library.id}`} aria-label={`Open ${library.name}`}
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

                    <p className="mt-6 absolute max-w-full px-3 wrap-anywhere text-center text-2xl font-bold">{library.name}</p>

                    <div className="absolute right-0 top-0 z-20 flex flex-row">
                        <Tooltip delay={0}>
                            <Tooltip.Trigger>
                                <Button isIconOnly variant="ghost"
                                        isDisabled={!hasActiveMetadataPlugins}
                                        aria-label="Scan library (quick)"
                                        onPress={() => triggerScan(ScanType.QUICK)}>
                                    <MagnifyingGlassIcon/>
                                </Button>
                            </Tooltip.Trigger>
                            <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                                Scan library (quick)
                            </Tooltip.Content>
                        </Tooltip>
                        <Tooltip delay={0}>
                            <Tooltip.Trigger>
                                <Button isIconOnly variant="ghost"
                                        isDisabled={!hasActiveMetadataPlugins}
                                        aria-label="Scan library (full)"
                                        onPress={() => triggerScan(ScanType.FULL)}>
                                    <MagnifyingGlassPlusIcon/>
                                </Button>
                            </Tooltip.Trigger>
                            <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                                Scan library (full)
                            </Tooltip.Content>
                        </Tooltip>
                    </div>
                </div>

                {stats &&
                    <div
                        className="relative grid grid-rows-2 grid-cols-3 justify-items-center items-center p-2 pt-4 [&_button]:relative [&_button]:z-20">
                        <p>Games</p>
                        <p>Downloads</p>
                        <p>Platforms</p>
                        <p className="font-bold">{stats.gamesCount}</p>
                        <p className="font-bold">{stats.downloadedGamesCount}</p>
                        <ChipList items={library.platforms ?? []} maxVisible={0} defaultContent="All"/>
                    </div>
                }
            </Card>
        </li>
    );
}
