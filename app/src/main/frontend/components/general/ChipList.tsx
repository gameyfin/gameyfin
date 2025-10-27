import React from "react";
import {Chip, Tooltip} from "@heroui/react";

interface ChipListProps {
    items: string[];
    maxVisible?: number;
    size?: "sm" | "md" | "lg";
    radius?: "none" | "sm" | "md" | "lg" | "full";
    defaultContent?: string;
}

export default function ChipList({items, maxVisible = 1, size = "sm", radius = "sm", defaultContent}: ChipListProps) {
    if (items.length === 0) {
        return defaultContent ? <Chip radius={radius} size={size}>{defaultContent}</Chip> : null;
    }

    const visibleItems = items.slice(0, maxVisible);
    const remainingItems = items.slice(maxVisible);
    const hasMore = remainingItems.length > 0;

    return (
        <div className="flex flex-row gap-1">
            {visibleItems.map(item => (
                <Chip key={item} radius={radius} size={size}>
                    {item}
                </Chip>
            ))}
            {hasMore && (
                <Tooltip
                    content={
                        <div className="flex flex-col gap-1">
                            {remainingItems.map(item => (
                                <div key={item}>{item}</div>
                            ))}
                        </div>
                    }
                    placement="right">
                    <Chip radius={radius} size={size}>
                        +{remainingItems.length}
                    </Chip>
                </Tooltip>
            )}
        </div>
    );
}

