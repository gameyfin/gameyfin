import {Card} from "@heroui/react";

interface GameCoverFallbackProps {
    title: string;
    size?: number;
    radius?: "none" | "sm" | "md" | "lg";
    hover?: boolean;
}

export function GameCoverFallback({title, size = 300, radius = "sm", hover = false}: GameCoverFallbackProps) {
    const radiusClass = {
        none: "rounded-none",
        sm: "rounded-sm",
        md: "rounded-md",
        lg: "rounded-lg",
    }[radius];

    return (
        <Card style={{aspectRatio: "12 /17", height: size}}
              className={`${radiusClass} ${hover ? "scale-95 hover:scale-100" : ""}`}>
            <div className="flex flex-col items-center justify-center h-full">
                {title}
            </div>
        </Card>
    );
}