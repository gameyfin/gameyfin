import React from "react";
import {Chip, Tooltip} from "@heroui/react";

interface ChipListProps {
    items: string[];
    maxVisible?: number;
    size?: "sm" | "md" | "lg";
    defaultContent?: string;
}

export default function ChipList({items, maxVisible = 1, size = "sm", defaultContent}: ChipListProps) {
    if (items.length === 0) {
        return defaultContent ? <Chip size={size}>{defaultContent}</Chip> : null;
    }

    const visibleItems = items.slice(0, maxVisible);
    const remainingItems = items.slice(maxVisible);
    const hasMore = remainingItems.length > 0;

    return (
        <div className="flex flex-row gap-1">
            {visibleItems.map(item => (
                <Chip key={item} size={size}>
                    {item}
                </Chip>
            ))}
            {hasMore && (
                <Tooltip>
                    <Tooltip.Trigger>
                        <Chip size={size}>
                            {maxVisible > 0 && "+"}{remainingItems.length}
                        </Chip>
                    </Tooltip.Trigger>
                    <Tooltip.Content placement="right">
                        <div className="flex flex-col gap-1">
                            {remainingItems.map(item => (
                                <div key={item}>{item}</div>
                            ))}
                        </div>
                    </Tooltip.Content>
                </Tooltip>
            )}
        </div>
    );
}
