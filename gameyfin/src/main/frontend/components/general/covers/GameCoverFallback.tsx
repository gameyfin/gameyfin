import {Card} from "@heroui/react";

interface GameCoverFallbackProps {
    title: string;
    size?: number;
    radius?: "none" | "sm" | "md" | "lg";
}

export function GameCoverFallback({title, size = 300, radius = "sm"}: GameCoverFallbackProps) {
    return (
        <Card style={{aspectRatio: "12 /17", height: size, borderRadius: radius}}
              radius={radius}>
            <div className="flex flex-col items-center justify-center h-full">
                {title}
            </div>
        </Card>
    );
}