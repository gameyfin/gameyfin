import {COVER_GAP as GAP, coverGridLayout} from "Frontend/util/coverGridLayout";
import {NARROW_QUERY, useMediaQuery} from "Frontend/util/useMediaQuery";
import GameSummaryDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSummaryDto";
import {type CoverBadge, GameCover} from "Frontend/components/general/covers/GameCover";
import type {PlayStatuses} from "Frontend/state/useMyPlayStatuses";
import {Grid} from "react-window";
import React, {useCallback, useEffect, useRef, useState} from "react";

interface CoverGridProps {
    games: GameSummaryDto[];
    coverBadges?: ReadonlyMap<number, readonly CoverBadge[]>;
    playStatuses?: PlayStatuses;
}

interface GridCellProps extends CoverGridProps {
    columnIndex: number;
    rowIndex: number;
    style: React.CSSProperties;
    columnCount: number;
    coverHeight: number;
}

export default function CoverGrid({games, coverBadges, playStatuses}: CoverGridProps) {
    const narrow = useMediaQuery(NARROW_QUERY);
    const containerRef = useRef<HTMLDivElement>(null);
    const [containerWidth, setContainerWidth] = useState(0);

    // Update container width on resize
    useEffect(() => {
        const updateDimensions = () => {
            if (containerRef.current) {
                setContainerWidth(containerRef.current.offsetWidth);
            }
        };

        const resizeObserver = new ResizeObserver(updateDimensions);
        if (containerRef.current) {
            resizeObserver.observe(containerRef.current);
        }

        updateDimensions();

        return () => resizeObserver.disconnect();
    }, []);

    const {columnCount, actualColumnWidth, coverHeight} = coverGridLayout(containerWidth, narrow);

    // Calculate row count
    const rowCount = Math.ceil(games.length / columnCount);


    // Cell renderer for react-window Grid
    const Cell = useCallback(({
                                  columnIndex,
                                  rowIndex,
                                  style,
                                  games: gamesData,
                                  coverBadges: badges,
                                  playStatuses: statuses,
                                  columnCount: colCount,
                                  coverHeight: height
                              }: GridCellProps) => {
        const gameIndex = rowIndex * colCount + columnIndex;

        // Return empty cell if we're past the end of the games array
        if (gameIndex >= gamesData.length) {
            return <div style={style}/>;
        }

        const game = gamesData[gameIndex];

        return (
            <div
                style={{
                    ...style,
                    paddingBottom: GAP,
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    boxSizing: 'border-box'
                }}
            >
                <GameCover key={game.id} game={game} interactive={true} size={height} lazy={true}
                           playStatus={statuses?.[game.id]} badges={badges?.get(game.id)}/>
            </div>
        );
    }, []);

    // Column width function to handle the last column differently
    const getColumnWidth = (index: number) => {
        // Last column doesn't need gap after it
        if (index === columnCount - 1) {
            return actualColumnWidth;
        }
        return actualColumnWidth + GAP;
    };

    return (
        <div ref={containerRef} className="w-full">
            {containerWidth > 0 && (
                <Grid<Omit<GridCellProps, "columnIndex" | "rowIndex" | "style">>
                    columnCount={columnCount}
                    columnWidth={getColumnWidth}
                    rowCount={rowCount}
                    rowHeight={coverHeight + GAP}
                    defaultWidth={containerWidth}
                    cellComponent={Cell}
                    cellProps={{games, coverBadges, playStatuses, columnCount, coverHeight}}
                    style={{overflowX: 'hidden'}}
                />
            )}
        </div>
    );
}
