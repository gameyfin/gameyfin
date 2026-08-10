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
        <Tooltip delay={0}>
            <Tooltip.Trigger>
                <InfoIcon size={16} weight="fill" className="ml-1 z-50"/>
            </Tooltip.Trigger>
            <Tooltip.Content placement="right">
                <Markdown
                    remarkPlugins={[remarkBreaks]}
                    components={{
                        a(props) {
                            return <Link href={props.href}
                                         target="_blank"
                                         rel="noopener noreferrer"
                                         className="text-foreground underline text-sm">
                                {props.children}
                                <Link.Icon/>
                            </Link>
                        }
                    }}
                >{content}</Markdown>
            </Tooltip.Content>
        </Tooltip>
    )
}