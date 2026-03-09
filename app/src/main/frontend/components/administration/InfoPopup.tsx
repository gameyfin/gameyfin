import {Link, Tooltip} from "@heroui/react";
import {InfoIcon} from "@phosphor-icons/react";
import Markdown from "react-markdown";
import remarkBreaks from "remark-breaks";
import React from "react";

interface InfoPopupProps {
    content: string;
}

export default function InfoPopup({content}: InfoPopupProps) {
    return (
        <Tooltip placement="right" content={
            <Markdown
                remarkPlugins={[remarkBreaks]}
                components={{
                    a(props) {
                        return <Link isExternal
                                     showAnchorIcon
                                     color="foreground"
                                     underline="always"
                                     href={props.href}
                                     size="sm">
                            {props.children}
                        </Link>
                    }
                }}
            >{content}</Markdown>
        }>
            <InfoIcon size={16} weight="fill" className="ml-1 z-50"/>
        </Tooltip>
    )
}