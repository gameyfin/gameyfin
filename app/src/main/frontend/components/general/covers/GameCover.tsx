import GameSummaryDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSummaryDto";
import PlayStatus from "Frontend/generated/org/gameyfin/app/social/PlayStatus";
import {GameCoverFallback} from "Frontend/components/general/covers/GameCoverFallback";
import {BookmarkSimpleIcon, CheckCircleIcon, GameControllerIcon, type Icon} from "@phosphor-icons/react";
import {memo, useEffect, useMemo, useRef, useState} from "react";
import {blurhashToDataUrl, imageRetryDelay, MAX_IMAGE_RETRIES, withRetryParam} from "Frontend/util/images";

// Cache to track which images have been loaded (and from which URL) across component remounts
const loadedImagesCache = new Map<number, string>();
const radiusClasses: Record<NonNullable<GameCoverProps["radius"]>, string> = {
    none: "rounded-none",
    sm: "rounded-sm",
    md: "rounded-md",
    lg: "rounded-lg",
};
const playStatusBadges = {
    [PlayStatus.PLAYED]: {Icon: CheckCircleIcon, label: "Played"},
    [PlayStatus.PLAYING]: {Icon: GameControllerIcon, label: "Playing"},
    [PlayStatus.WANT_TO_PLAY]: {Icon: BookmarkSimpleIcon, label: "Want to play"}
};

export interface CoverBadge {
    text: string;
    icon?: Icon;
    description?: string;
}

interface GameCoverProps {
    /** Works with both summary and full DTOs */
    game: Pick<GameSummaryDto, "id" | "title" | "cover">;
    size?: number;
    radius?: "none" | "sm" | "md" | "lg";
    interactive?: boolean;
    lazy?: boolean;
    playStatus?: PlayStatus;
    badges?: readonly CoverBadge[];
    /** Whether the title is shown while the cover image is not available */
    showTitle?: boolean;
}

const GameCoverComponent = ({
                                game,
                                size = 300,
                                radius = "sm",
                                interactive = false,
                                lazy = false,
                                playStatus,
                                badges,
                                showTitle = true
                            }: GameCoverProps) => {
    const [shouldLoad, setShouldLoad] = useState(!lazy);
    // URL the image has been loaded from, the cache is checked to see if this image has already been loaded
    const [loadedUrl, setLoadedUrl] = useState<string | undefined>(
        game.cover ? loadedImagesCache.get(game.cover.id) : undefined
    );
    const [hasImageError, setHasImageError] = useState(false);
    // Images which could not be loaded (e.g. because they are still being downloaded) are retried
    const [attempt, setAttempt] = useState(0);
    const containerRef = useRef<HTMLDivElement>(null);
    const prevCoverIdRef = useRef<number | undefined>(game.cover?.id);

    // Reset state when cover ID changes
    useEffect(() => {
        const currentCoverId = game.cover?.id;
        if (prevCoverIdRef.current !== currentCoverId) {
            prevCoverIdRef.current = currentCoverId;
            setLoadedUrl(currentCoverId ? loadedImagesCache.get(currentCoverId) : undefined);
            setHasImageError(false);
            setAttempt(0);
            setShouldLoad(!lazy);
        }
    }, [game.cover?.id, lazy]);

    // Blurhash placeholder image, shown while the cover is loading or not available
    const blurhashUrl = useMemo(
        () => game.cover?.blurhash ? blurhashToDataUrl(game.cover.blurhash, 32, 45) : undefined,
        [game.cover?.blurhash]
    );

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
        if (!shouldLoad || !game.cover || loadedUrl || hasImageError) return;

        const coverId = game.cover.id;
        const url = withRetryParam(`images/cover/${coverId}`, attempt);
        let cancelled = false;
        const img = document.createElement('img');
        img.onload = () => {
            if (cancelled) return;
            loadedImagesCache.set(coverId, url);
            setLoadedUrl(url);
        };
        img.onerror = () => {
            if (!cancelled) setHasImageError(true);
        };
        img.src = url;
        return () => {
            cancelled = true;
        };
    }, [shouldLoad, game.cover, loadedUrl, hasImageError, attempt]);

    // Retry loading the image with an increasing delay
    useEffect(() => {
        if (!hasImageError || attempt >= MAX_IMAGE_RETRIES) return;
        const timeout = setTimeout(() => {
            setAttempt(attempt + 1);
            setHasImageError(false);
        }, imageRetryDelay(attempt));
        return () => clearTimeout(timeout);
    }, [hasImageError, attempt]);

    // Once loading the image failed, the title is shown until it is available
    const isUnavailable = hasImageError || attempt > 0;
    const hasCoverBadges = interactive && !!badges?.length;

    const coverContent = game.cover && (loadedUrl || blurhashUrl || !isUnavailable) ? (
        <div ref={containerRef}>
            {loadedUrl ? (
                <img
                    alt={game.title}
                    className={`z-0 object-cover aspect-12/17 ${radiusClasses[radius]}`}
                    src={loadedUrl}
                    style={{height: size}}
                    loading={lazy ? "lazy" : "eager"}
                />
            ) : blurhashUrl ? (
                <div className={`relative overflow-hidden aspect-12/17 ${radiusClasses[radius]}`}
                     style={{height: size}}>
                    <img
                        alt={game.title}
                        className="z-0 h-full w-full object-cover"
                        src={blurhashUrl}
                    />
                    {showTitle && isUnavailable &&
                        <div className="absolute inset-0 flex items-center justify-center p-2">
                            <p className={`px-2 py-1 rounded-md bg-black/50 backdrop-blur-sm text-white text-center line-clamp-4 shadow-lg ${size < 150 ? "text-xs" : ""}`}>
                                {game.title}
                            </p>
                        </div>
                    }
                </div>
            ) : (
                <div
                    className={`bg-default animate-pulse aspect-12/17 ${radiusClasses[radius]}`}
                    style={{height: size}}
                />
            )}
        </div>
    ) : (
        <GameCoverFallback title={game.title} size={size} radius={radius} showTitle={showTitle}
                           titlePlacement={hasCoverBadges ? "top" : "bottom"}/>
    );

    const badge = playStatus && playStatusBadges[playStatus];
    return interactive ? (
        <a href={`/game/${game.id}`} className="block w-fit rounded-md scale-95 hover:scale-100 shine transition-all">
            {coverContent}
            {badge && (
                <span title={badge.label} className="absolute top-2 right-2 z-10 rounded-full bg-black/80 p-1 text-white">
                    <badge.Icon size={18} weight="bold" aria-hidden="true"/>
                    <span className="sr-only">{badge.label}</span>
                </span>
            )}
            {hasCoverBadges && (
                <div className="absolute inset-x-2 bottom-2 z-10 flex flex-col items-start gap-1">
                    {badges.map(({text, icon: Icon, description}, index) => (
                        <span key={index} title={description}
                              className="flex max-w-full items-center gap-1 rounded-full bg-black/80 px-2 py-1 text-xs font-semibold text-white">
                            {Icon && <Icon size={14} className="shrink-0" aria-hidden="true"/>}
                            <span className="truncate" aria-hidden={description ? true : undefined}>{text}</span>
                            {description && <span className="sr-only">{description}</span>}
                        </span>
                    ))}
                </div>
            )}
        </a>
    ) : coverContent;
};

// Memoize the component to prevent unnecessary re-renders
export const GameCover = memo(GameCoverComponent, (prevProps, nextProps) => {
    return prevProps.game.id === nextProps.game.id &&
        prevProps.game.cover?.id === nextProps.game.cover?.id &&
        prevProps.size === nextProps.size &&
        prevProps.radius === nextProps.radius &&
        prevProps.interactive === nextProps.interactive &&
        prevProps.lazy === nextProps.lazy &&
        prevProps.playStatus === nextProps.playStatus &&
        prevProps.badges === nextProps.badges &&
        prevProps.showTitle === nextProps.showTitle;
});
