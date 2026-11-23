import {COVER_GAP as GAP, coverGridLayout} from "Frontend/util/coverGridLayout";
import {NARROW_QUERY, useMediaQuery} from "Frontend/util/useMediaQuery";
import GameSummaryDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSummaryDto";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import type {PlayStatuses} from "Frontend/state/useMyPlayStatuses";
import {Grid} from "react-window";
import React, {useCallback, useEffect, useRef, useState} from "react";

interface CoverGridProps {
    games: GameSummaryDto[];
    captions?: ReadonlyMap<number, string>;
    playStatuses?: PlayStatuses;
}

interface GridCellProps extends CoverGridProps {
    columnIndex: number;
    rowIndex: number;
    style: React.CSSProperties;
    columnCount: number;
    coverHeight: number;
}

export default function CoverGrid({games, captions, playStatuses}: CoverGridProps) {
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
    const captionHeight = captions?.size ? 72 : 0;

    // Calculate row count
    const rowCount = Math.ceil(games.length / columnCount);


    // Cell renderer for react-window Grid
    const Cell = useCallback(({
                                  columnIndex,
                                  rowIndex,
                                  style,
                                  games: gamesData,
                                  captions: gameCaptions,
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
        const caption = gameCaptions?.get(game.id);

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
                           playStatus={statuses?.[game.id]}/>
                {caption && <p className="px-1 pt-2 text-sm leading-4 text-muted text-center line-clamp-4 wrap-anywhere"
                               title={caption} style={{maxWidth: height * 12 / 17}}>{caption}</p>}
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
                    rowHeight={coverHeight + GAP + captionHeight}
                    defaultWidth={containerWidth}
                    cellComponent={Cell}
                    cellProps={{games, captions, playStatuses, columnCount, coverHeight}}
                    style={{overflowX: 'hidden'}}
                />
            )}
        </div>
    );
}
