import React, {useCallback, useEffect, useRef, useState} from "react";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {CaretLeftIcon, CaretRightIcon} from "@phosphor-icons/react";
import {Button, Link} from "@heroui/react";
import {Grid, GridImperativeAPI} from "react-window";

interface CoverRowProps {
    games: GameDto[];
    title: string;
    link: string;
}

const aspectRatio = 12 / 17; // aspect ratio of the game cover
const defaultImageHeight = 300; // default height for the image
const defaultImageWidth = aspectRatio * defaultImageHeight; // default width for the image
const gap = 8; // gap between items in pixels (gap-2 = 0.5rem = 8px)

export function CoverRow({games, title, link}: CoverRowProps) {
    const gridRef = useRef<GridImperativeAPI | null>(null);
    const [scrollPosition, setScrollPosition] = useState(0);
    const [containerWidth, setContainerWidth] = useState(0);
    const containerRef = useRef<HTMLDivElement>(null);

    // Update container width on resize
    useEffect(() => {
        const updateWidth = () => {
            if (containerRef.current) {
                setContainerWidth(containerRef.current.offsetWidth);
            }
        };

        const resizeObserver = new ResizeObserver(updateWidth);
        if (containerRef.current) {
            resizeObserver.observe(containerRef.current);
        }

        updateWidth();

        return () => resizeObserver.disconnect();
    }, []);

    // Handle scroll updates - track scroll position from the grid element
    useEffect(() => {
        let gridElement: HTMLDivElement | null = null;

        const handleScroll = () => {
            if (gridElement) {
                setScrollPosition(gridElement.scrollLeft);
            }
        };

        // Small delay to ensure grid is mounted
        const timer = setTimeout(() => {
            gridElement = gridRef.current?.element ?? null;
            if (gridElement) {
                gridElement.addEventListener('scroll', handleScroll);
                // Initial scroll position
                setScrollPosition(gridElement.scrollLeft);
            }
        }, 100);

        return () => {
            clearTimeout(timer);
            if (gridElement) {
                gridElement.removeEventListener('scroll', handleScroll);
            }
        };
    }, [containerWidth, games.length]);

    const totalWidth = games.length * (defaultImageWidth + gap);
    const maxScroll = Math.max(0, totalWidth - containerWidth);

    const scrollLeft = () => {
        const gridElement = gridRef.current?.element;
        if (gridElement) {
            const itemWidth = defaultImageWidth + gap;
            const scrollAmount = itemWidth * 3; // Scroll exactly 3 items
            const newPosition = Math.max(0, scrollPosition - scrollAmount);
            gridElement.scrollTo({
                left: newPosition,
                behavior: "smooth"
            });
        }
    };

    const scrollRight = () => {
        const gridElement = gridRef.current?.element;
        if (gridElement) {
            const itemWidth = defaultImageWidth + gap;
            const scrollAmount = itemWidth * 3; // Scroll exactly 3 items
            const newPosition = Math.min(maxScroll, scrollPosition + scrollAmount);
            gridElement.scrollTo({
                left: newPosition,
                behavior: "smooth"
            });
        }
    };

    const canScrollLeft = scrollPosition > 1; // Allow small margin for floating point issues
    const canScrollRight = scrollPosition < maxScroll - 1 && maxScroll > 0;

    // Define interface for Cell props
    interface RowCellProps {
        columnIndex: number;
        rowIndex: number;
        style: React.CSSProperties;
        games: GameDto[];
    }

    // Cell renderer for react-window Grid
    const Cell = useCallback(({columnIndex, style, games: gamesData}: RowCellProps) => {
        const game = gamesData[columnIndex];
        return (
            <div style={{...style, paddingRight: gap}}>
                <GameCover key={game.id} game={game} radius="sm" interactive={true}/>
            </div>
        );
    }, []);

    return (
        <div className="flex flex-col mb-4">
            <div className="flex flex-row justify-between items-baseline mb-4">
                <Link href={link} className="flex flex-row gap-1 w-fit items-baseline" color="foreground"
                      underline="hover">
                    <p className="text-2xl font-bold">{title}</p>
                    <CaretRightIcon weight="bold" size={16}/>
                </Link>
                <div className="flex flex-row gap-2">
                    <Button
                        isIconOnly
                        size="sm"
                        variant="flat"
                        onPress={scrollLeft}
                        isDisabled={!canScrollLeft}
                        aria-label="Scroll left"
                    >
                        <CaretLeftIcon weight="bold" size={20}/>
                    </Button>
                    <Button
                        isIconOnly
                        size="sm"
                        variant="flat"
                        onPress={scrollRight}
                        isDisabled={!canScrollRight}
                        aria-label="Scroll right"
                    >
                        <CaretRightIcon weight="bold" size={20}/>
                    </Button>
                </div>
            </div>
            <div ref={containerRef} className="w-full relative overflow-hidden">
                {containerWidth > 0 && (
                    <Grid<{ games: GameDto[] }>
                        gridRef={gridRef}
                        columnCount={games.length}
                        columnWidth={defaultImageWidth + gap}
                        rowCount={1}
                        rowHeight={defaultImageHeight}
                        defaultHeight={defaultImageHeight}
                        defaultWidth={containerWidth}
                        cellComponent={Cell}
                        cellProps={{games}}
                        className="scrollbar-hide"
                        style={{overflow: 'auto'}}
                    />
                )}
            </div>
        </div>
    );
}
