import Popup from "Frontend/components/general/Popup";
import {InfoIcon} from "@phosphor-icons/react";
import MarkdownContent from "Frontend/components/general/MarkdownContent";
import React from "react";

interface InfoPopupProps {
    content: string;
}

export default function InfoPopup({content}: InfoPopupProps) {
    return (
        <Popup>
            <Popup.Trigger aria-label="More information" className="flex">
                <span className="ml-1 relative z-50 inline-flex">
                    <InfoIcon size={16} weight="fill" className="block"/>
                </span>
            </Popup.Trigger>
            <Popup.Content placement="left" className="max-w-xs [word-break:normal] wrap-normal">
                <MarkdownContent className="text-xs">{content}</MarkdownContent>
            </Popup.Content>
        </Popup>
    )
}
