import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {Image} from "@heroui/react";
import {GameCoverFallback} from "Frontend/components/general/covers/GameCoverFallback";
import {memo, useEffect, useRef, useState} from "react";
import {decode} from "blurhash";

// Cache to track which images have been loaded across component remounts
const loadedImagesCache = new Set<number>();

interface GameCoverProps {
    game: GameDto;
    size?: number;
    radius?: "none" | "sm" | "md" | "lg";
    interactive?: boolean;
    lazy?: boolean;
}

const GameCoverComponent = ({game, size = 300, radius = "sm", interactive = false, lazy = false}: GameCoverProps) => {
    const [shouldLoad, setShouldLoad] = useState(!lazy);
    // Check cache to see if this image has already been loaded
    const isCached = game.cover ? loadedImagesCache.has(game.cover.id) : false;
    const [isImageLoaded, setIsImageLoaded] = useState(isCached);
    const [blurhashUrl, setBlurhashUrl] = useState<string | undefined>(undefined);
    const containerRef = useRef<HTMLDivElement>(null);

    // Generate blurhash placeholder image
    useEffect(() => {
        if (game.cover?.blurhash && !blurhashUrl) {
            try {
                // Decode blurhash to pixel data
                const pixels = decode(game.cover.blurhash, 32, 45); // Small size for placeholder

                // Create canvas and draw pixels
                const canvas = document.createElement('canvas');
                canvas.width = 32;
                canvas.height = 45;
                const ctx = canvas.getContext('2d');

                if (ctx) {
                    const imageData = ctx.createImageData(32, 45);
                    imageData.data.set(pixels);
                    ctx.putImageData(imageData, 0, 0);

                    // Convert canvas to data URL
                    setBlurhashUrl(canvas.toDataURL());
                }
            } catch (e) {
                console.error('Failed to decode blurhash:', e);
            }
        }
    }, [game.cover?.blurhash, blurhashUrl]);

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

    // Preload the real image when shouldLoad becomes true
    useEffect(() => {
        if (!shouldLoad || !game.cover || isImageLoaded) return;

        const img = document.createElement('img');
        img.src = `images/cover/${game.cover.id}`;
        img.onload = () => {
            loadedImagesCache.add(game.cover!.id);
            setIsImageLoaded(true);
        };
        img.onerror = () => {
            // If image fails to load, we'll just show the fallback
            setIsImageLoaded(true);
        };
    }, [shouldLoad, game.cover, isImageLoaded]);

    const coverContent = game.cover ? (
        <div
            ref={containerRef}
            className={`${interactive ? "rounded-md scale-95 hover:scale-100 shine transition-all" : ""}`}
        >
            <Image
                alt={game.title}
                className="z-0 object-cover aspect-12/17"
                src={(shouldLoad || isCached) && isImageLoaded ? `images/cover/${game.cover.id}` : blurhashUrl}
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
};

// Memoize the component to prevent unnecessary re-renders
// Only re-render if the game ID, size, radius, interactive, or lazy props change
export const GameCover = memo(GameCoverComponent, (prevProps, nextProps) => {
    return prevProps.game.id === nextProps.game.id &&
        prevProps.size === nextProps.size &&
        prevProps.radius === nextProps.radius &&
        prevProps.interactive === nextProps.interactive &&
        prevProps.lazy === nextProps.lazy;
});
