import {Tooltip} from "@heroui/react";
import {InfoIcon} from "@phosphor-icons/react";
import MarkdownContent from "Frontend/components/general/MarkdownContent";
import React from "react";

interface InfoPopupProps {
    content: string;
}

export default function InfoPopup({content}: InfoPopupProps) {
    return (
        <Tooltip delay={0}>
            <Tooltip.Trigger className="flex">
                <span className="ml-1 relative z-50 inline-flex">
                    <InfoIcon size={16} weight="fill" className="block"/>
                </span>
            </Tooltip.Trigger>
            <Tooltip.Content placement="left" className="max-w-xs [word-break:normal] wrap-normal">
                <MarkdownContent className="text-xs">{content}</MarkdownContent>
            </Tooltip.Content>
        </Tooltip>
    )
}
