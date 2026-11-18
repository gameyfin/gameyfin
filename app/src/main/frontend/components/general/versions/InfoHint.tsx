import React from "react";
import {Tooltip} from "@heroui/react";
import {InfoIcon} from "@phosphor-icons/react";

/** A small (!) icon that explains a section on hover or focus. */
export default function InfoHint({text}: { text: string }) {
    return (
        <Tooltip>
            <Tooltip.Trigger>
                <span tabIndex={0} role="img" aria-label={text} className="inline-flex text-muted"><InfoIcon size={16}/></span>
            </Tooltip.Trigger>
            <Tooltip.Content placement="right">
                <span className="block max-w-64">{text}</span>
            </Tooltip.Content>
        </Tooltip>
    );
}
