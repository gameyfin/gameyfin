import React, {useCallback, useEffect, useRef, useState} from "react";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import GameSummaryDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSummaryDto";
import {CaretLeftIcon, CaretRightIcon} from "@phosphor-icons/react";
import {Button, Link} from "@heroui/react";
import {Grid, GridImperativeAPI} from "react-window";

interface CoverRowProps {
    games: GameSummaryDto[];
    title: string;
    link: string;
}

const aspectRatio = 12 / 17; // aspect ratio of the game cover
const defaultImageHeight = 300; // default height for the image
const defaultImageWidth = aspectRatio * defaultImageHeight; // default width for the image
const gap = 8; // gap between items in pixels (gap-2 = 0.5rem = 8px)
const columnWidth = defaultImageWidth + gap;
const mouseDragThreshold = 5;
const releaseVelocityWindow = 100; // ms before a stationary pointer loses its momentum
const momentumProjectionTime = 160; // ms of travel used to choose the snap point

interface MouseDrag {
    pointerId: number;
    startX: number;
    startScrollLeft: number;
    lastX: number;
    lastMoveTime: number;
    velocity: number;
}

export function CoverRow({games, title, link}: CoverRowProps) {
    const gridRef = useRef<GridImperativeAPI | null>(null);
    const [scrollPosition, setScrollPosition] = useState(0);
    const [containerWidth, setContainerWidth] = useState(0);
    const containerRef = useRef<HTMLDivElement>(null);
    const mouseDrag = useRef<MouseDrag | null>(null);
    const momentumFrame = useRef<number | null>(null);

    const stopMomentum = useCallback(() => {
        if (momentumFrame.current !== null) {
            cancelAnimationFrame(momentumFrame.current);
            momentumFrame.current = null;
        }
    }, []);

    useEffect(() => stopMomentum, [stopMomentum]);

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

    const totalWidth = games.length * columnWidth;
    const maxScroll = Math.max(0, totalWidth - containerWidth);

    const scrollLeft = () => {
        stopMomentum();
        const gridElement = gridRef.current?.element;
        if (gridElement) {
            const scrollAmount = columnWidth * 3; // Scroll exactly 3 items
            const newPosition = Math.max(0, scrollPosition - scrollAmount);
            gridElement.scrollTo({
                left: newPosition,
                behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth"
            });
        }
    };

    const scrollRight = () => {
        stopMomentum();
        const gridElement = gridRef.current?.element;
        if (gridElement) {
            const scrollAmount = columnWidth * 3; // Scroll exactly 3 items
            const newPosition = Math.min(maxScroll, scrollPosition + scrollAmount);
            gridElement.scrollTo({
                left: newPosition,
                behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth"
            });
        }
    };

    const canScrollLeft = scrollPosition > 1; // Allow small margin for floating point issues
    const canScrollRight = scrollPosition < maxScroll - 1 && maxScroll > 0;

    const startMouseDrag = (event: React.PointerEvent<HTMLDivElement>) => {
        stopMomentum();
        const gridElement = gridRef.current?.element;
        if (event.pointerType !== "mouse" || event.button !== 0 || !gridElement ||
            gridElement.scrollWidth <= gridElement.clientWidth) return;

        mouseDrag.current = {
            pointerId: event.pointerId,
            startX: event.clientX,
            startScrollLeft: gridElement.scrollLeft,
            lastX: event.clientX,
            lastMoveTime: event.timeStamp,
            velocity: 0
        };
    };

    const moveMouseDrag = (event: React.PointerEvent<HTMLDivElement>) => {
        const drag = mouseDrag.current;
        const gridElement = gridRef.current?.element;
        if (!drag || drag.pointerId !== event.pointerId || !gridElement) return;

        const distance = event.clientX - drag.startX;
        if (!event.currentTarget.hasPointerCapture(event.pointerId)) {
            if (Math.abs(distance) < mouseDragThreshold) return;
            // Capture only after movement so a regular click still reaches the cover link.
            event.currentTarget.setPointerCapture(event.pointerId);
        }

        gridElement.scrollLeft = drag.startScrollLeft - distance;

        if (event.clientX !== drag.lastX) {
            const elapsed = event.timeStamp - drag.lastMoveTime;
            const velocity = (drag.lastX - event.clientX) / Math.max(elapsed, 8);
            drag.velocity = elapsed < releaseVelocityWindow ? (drag.velocity + velocity) / 2 : velocity;
            drag.lastX = event.clientX;
            drag.lastMoveTime = event.timeStamp;
        }
    };

    const releaseMouseDrag = (event: React.PointerEvent<HTMLDivElement>) => {
        const drag = mouseDrag.current;
        if (!drag || drag.pointerId !== event.pointerId) return;
        mouseDrag.current = null;

        const gridElement = gridRef.current?.element;
        if (!gridElement || !event.currentTarget.hasPointerCapture(event.pointerId)) return;

        const start = gridElement.scrollLeft;
        const end = gridElement.scrollWidth - gridElement.clientWidth;
        const timeSinceMove = event.timeStamp - drag.lastMoveTime;
        const velocity = drag.velocity * Math.max(0, 1 - timeSinceMove / releaseVelocityWindow);
        const momentum = Math.max(-3 * columnWidth, Math.min(3 * columnWidth, velocity * momentumProjectionTime));
        const projected = Math.max(0, Math.min(end, start + momentum));
        const lastAlignedCover = Math.floor(end / columnWidth);
        const aligned = Math.min(lastAlignedCover, Math.round(projected / columnWidth)) * columnWidth;
        const target = end - projected <= Math.abs(projected - aligned) ? end : aligned;

        const distance = target - start;
        if (Math.abs(distance) < 1 || window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
            gridElement.scrollLeft = target;
            return;
        }

        const duration = Math.min(520, 200 + Math.abs(distance) * 0.55);
        const startedAt = performance.now();
        const animate = (now: number) => {
            const progress = Math.min(1, (now - startedAt) / duration);
            const eased = 1 - (1 - progress) ** 3;
            gridElement.scrollLeft = start + distance * eased;
            momentumFrame.current = progress < 1 ? requestAnimationFrame(animate) : null;
        };
        momentumFrame.current = requestAnimationFrame(animate);
    };

    const endMouseDrag = (event: React.PointerEvent<HTMLDivElement>) => {
        if (mouseDrag.current?.pointerId === event.pointerId) {
            mouseDrag.current = null;
        }
    };

    // Define interface for Cell props
    interface RowCellProps {
        columnIndex: number;
        rowIndex: number;
        style: React.CSSProperties;
        games: GameSummaryDto[];
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
            <div className="flex flex-wrap gap-2 justify-between items-baseline mb-4">
                <Link href={link}
                      className="flex flex-row gap-1 w-fit items-baseline text-foreground hover:underline underline-offset-2">
                    <p className="text-2xl font-bold">{title}</p>
                    <CaretRightIcon weight="bold" size={16}/>
                </Link>
                <div className="flex flex-row gap-2">
                    <Button
                        isIconOnly
                        size="sm"
                        variant="ghost"
                        onPress={scrollLeft}
                        isDisabled={!canScrollLeft}
                        aria-label="Scroll left"
                    >
                        <CaretLeftIcon weight="bold" size={20}/>
                    </Button>
                    <Button
                        isIconOnly
                        size="sm"
                        variant="ghost"
                        onPress={scrollRight}
                        isDisabled={!canScrollRight}
                        aria-label="Scroll right"
                    >
                        <CaretRightIcon weight="bold" size={20}/>
                    </Button>
                </div>
            </div>
            <div ref={containerRef}
                 className="w-full relative overflow-hidden"
                 onPointerDown={startMouseDrag}
                 onPointerMove={moveMouseDrag}
                 onPointerUp={releaseMouseDrag}
                 onPointerCancel={endMouseDrag}
                 onLostPointerCapture={endMouseDrag}
                 onPointerLeave={endMouseDrag}
                 onWheel={stopMomentum}
                 onDragStart={event => {
                     if (mouseDrag.current) event.preventDefault();
                 }}>
                {containerWidth > 0 && (
                    <Grid<{ games: GameSummaryDto[] }>
                        gridRef={gridRef}
                        columnCount={games.length}
                        columnWidth={columnWidth}
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
