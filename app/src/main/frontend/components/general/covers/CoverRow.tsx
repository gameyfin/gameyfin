import React, {useEffect, useRef, useState} from "react";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {ArrowRight} from "@phosphor-icons/react";
import {useNavigate} from "react-router";

interface CoverRowProps {
    games: GameDto[];
    title: string;
    onPressShowMore: () => void;
}

const aspectRatio = 12 / 17; // aspect ratio of the game cover
const defaultImageHeight = 300; // default height for the image
const defaultImageWidth = aspectRatio * defaultImageHeight; // default width for the image

export function CoverRow({games, title, onPressShowMore}: CoverRowProps) {

    const navigate = useNavigate();
    const containerRef = useRef<HTMLDivElement>(null);
    const [visibleCount, setVisibleCount] = useState(games.length);

    useEffect(() => {
        const calculateVisible = () => {
            if (containerRef.current) {
                const containerWidth = containerRef.current.offsetWidth;
                const maxFit = Math.floor((containerWidth - defaultImageWidth) / defaultImageWidth) + 1;
                setVisibleCount(maxFit < games.length ? maxFit : games.length);
            }
        };

        const resizeObserver = new ResizeObserver(calculateVisible);
        if (containerRef.current) {
            resizeObserver.observe(containerRef.current);
        }

        calculateVisible(); // initial calculation

        return () => resizeObserver.disconnect();
    }, [games.length]);

    const showMore = visibleCount < games.length;

    return (
        <div className="flex flex-col mb-4">
            <p className="text-2xl font-bold mb-4">{title}</p>
            <div className="w-full relative">
                <div ref={containerRef} className="flex flex-row gap-2 rounded-md bg-transparent">
                    {games.slice(0, visibleCount).map((game, index) => (
                        <GameCover key={index} game={game} radius="sm" interactive={true}/>
                    ))}
                </div>

                {showMore && (
                    <div className="flex flex-row items-center justify-end cursor-pointer"
                         onClick={onPressShowMore}>
                        <div className="absolute h-full w-1/4 right-0 bottom-0
                        bg-linear-to-r from-transparent to-background
                        transition-all duration-300 ease-in-out hover:opacity-80"/>
                        <div
                            className="absolute h-full right-0 bottom-0 flex flex-row items-center gap-2 pointer-events-none">
                            <p className="text-xl font-semibold">Show more</p>
                            <ArrowRight weight="bold"/>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}
