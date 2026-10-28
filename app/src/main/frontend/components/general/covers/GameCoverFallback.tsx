import {Card} from "@heroui/react";
import {DiscIcon} from "@phosphor-icons/react";

interface GameCoverFallbackProps {
    title: string;
    size?: number;
    radius?: "none" | "sm" | "md" | "lg";
    hover?: boolean;
    showTitle?: boolean;
}

export function GameCoverFallback({
                                      title,
                                      size = 300,
                                      radius = "sm",
                                      hover = false,
                                      showTitle = true
                                  }: GameCoverFallbackProps) {
    const iconSize = size * 0.5;
    const radiusClass = {
        none: "rounded-none",
        sm: "rounded-sm",
        md: "rounded-md",
        lg: "rounded-lg",
    }[radius];

    return (
        <Card style={{aspectRatio: "12 / 17", height: size, width: size * 12 / 17}}
              aria-label={title}
              className={`min-w-0 shrink-0 overflow-hidden p-0 ${radiusClass} ${hover ? "scale-95 hover:scale-100 shine transition-all" : ""}`}>
            <div aria-hidden="true" className="pointer-events-none absolute inset-0 gradient-primary opacity-30"/>
            <DiscIcon weight="fill" size={iconSize} aria-hidden="true"
                      className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 text-muted"/>
            {showTitle && (
                <p className="absolute inset-x-3 text-center wrap-break-word line-clamp-4"
                   style={{top: "50%", marginTop: iconSize / 2 + 12}}>
                    {title}
                </p>
            )}
        </Card>
    );
}
