import React from "react";
import {Tooltip} from "@heroui/react";
import {InfoIcon} from "@phosphor-icons/react";

/** A small (!) icon that explains a section on hover or focus. */
export default function InfoHint({text}: { text: string }) {
    return (
        <Tooltip>
            <Tooltip.Trigger>
                <InfoIcon size={16}/>
            </Tooltip.Trigger>
            <Tooltip.Content placement="right">
                <span className="block max-w-64">{text}</span>
            </Tooltip.Content>
        </Tooltip>
    );
}
