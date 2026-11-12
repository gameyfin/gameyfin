import {COVER_GAP as GAP, coverGridLayout} from "Frontend/util/coverGridLayout";
import {NARROW_QUERY, useMediaQuery} from "Frontend/util/useMediaQuery";
import GameSummaryDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSummaryDto";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import {Grid} from "react-window";
import React, {useCallback, useEffect, useRef, useState} from "react";

interface CoverGridProps {
    games: GameSummaryDto[];
}

interface GridCellProps {
    columnIndex: number;
    rowIndex: number;
    style: React.CSSProperties;
    games: GameSummaryDto[];
    columnCount: number;
    coverHeight: number;
}

export default function CoverGrid({games}: CoverGridProps) {
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
                    justifyContent: 'center',
                    boxSizing: 'border-box'
                }}
            >
                <GameCover key={game.id} game={game} interactive={true} size={height} lazy={true}/>
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
                <Grid<{ games: GameSummaryDto[], columnCount: number, coverHeight: number }>
                    columnCount={columnCount}
                    columnWidth={getColumnWidth}
                    rowCount={rowCount}
                    rowHeight={coverHeight + GAP}
                    defaultWidth={containerWidth}
                    cellComponent={Cell}
                    cellProps={{games, columnCount, coverHeight}}
                    style={{overflowX: 'hidden'}}
                />
            )}
        </div>
    );
}