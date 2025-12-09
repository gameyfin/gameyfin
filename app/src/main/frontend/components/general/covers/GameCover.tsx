import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {Image} from "@heroui/react";
import {GameCoverFallback} from "Frontend/components/general/covers/GameCoverFallback";
import {useEffect, useRef, useState} from "react";

interface GameCoverProps {
    game: GameDto;
    size?: number;
    radius?: "none" | "sm" | "md" | "lg";
    interactive?: boolean;
    lazy?: boolean;
}

export function GameCover({game, size = 300, radius = "sm", interactive = false, lazy = false}: GameCoverProps) {
    const [shouldLoad, setShouldLoad] = useState(!lazy);
    const containerRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!lazy || shouldLoad) return;

        const observer = new IntersectionObserver(
            (entries) => {
                entries.forEach((entry) => {
                    if (entry.isIntersecting) {
                        setShouldLoad(true);
                        observer.disconnect();
                    }
                });
            },
            {
                rootMargin: '200px', // Start loading 200px before the element enters viewport
            }
        );

        if (containerRef.current) {
            observer.observe(containerRef.current);
        }

        return () => observer.disconnect();
    }, [lazy, shouldLoad]);

    const coverContent = Number.isInteger(game.coverId) ? (
        <div
            ref={containerRef}
            className={`${interactive ? "rounded-md scale-95 hover:scale-100 shine transition-all" : ""}`}
        >
            <Image
                alt={game.title}
                className="z-0 object-cover aspect-12/17"
                src={shouldLoad ? `images/cover/${game.coverId}` : undefined}
                radius={radius}
                height={size}
                fallbackSrc={<GameCoverFallback title={game.title} size={size} radius={radius}/>}
            />
        </div>
    ) : (
        <GameCoverFallback title={game.title} size={size} radius={radius} hover={interactive}/>
    );

    return interactive ? (
        <a href={`/game/${game.id}`}>
            {coverContent}
        </a>
    ) : coverContent;
}