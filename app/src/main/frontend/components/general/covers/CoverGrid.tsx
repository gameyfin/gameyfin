import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import {Grid} from "react-window";
import React, {useCallback, useEffect, useRef, useState} from "react";

interface CoverGridProps {
    games: GameDto[];
}

interface GridCellProps {
    columnIndex: number;
    rowIndex: number;
    style: React.CSSProperties;
    games: GameDto[];
    columnCount: number;
    coverHeight: number;
}

// Constants for grid layout
const MIN_COLUMN_WIDTH = 180; // Minimum width per item (minmax value from original)
const MAX_COLUMN_WIDTH = 212; // Maximum width per item (minmax value from original)
const GAP = 16; // gap-4 = 1rem = 16px
const ASPECT_RATIO = 12 / 17; // Game cover aspect ratio (width/height)

export default function CoverGrid({games}: CoverGridProps) {
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

    // Calculate how many columns can fit
    const columnCount = Math.max(1, Math.floor((containerWidth + GAP) / (MIN_COLUMN_WIDTH + GAP)));

    // Calculate actual column width to distribute space evenly (up to MAX_COLUMN_WIDTH)
    const actualColumnWidth = Math.min(
        MAX_COLUMN_WIDTH,
        Math.floor((containerWidth - (columnCount - 1) * GAP) / columnCount)
    );

    // Calculate cover height based on width and aspect ratio
    // GameCover's size prop is the height, so we need to calculate height from width
    const coverHeight = Math.floor(actualColumnWidth / ASPECT_RATIO);

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
                <Grid<{ games: GameDto[], columnCount: number, coverHeight: number }>
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