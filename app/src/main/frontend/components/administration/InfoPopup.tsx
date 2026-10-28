import {Tooltip} from "@heroui/react";
import {ArrowUpRightIcon, InfoIcon} from "@phosphor-icons/react";
import Markdown from "react-markdown";
import remarkBreaks from "remark-breaks";
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
                <Markdown
                    remarkPlugins={[remarkBreaks]}
                    components={{
                        a(props) {
                            return <a href={props.href}
                                      target="_blank"
                                      rel="noopener noreferrer"
                                      className="text-foreground underline text-xs inline-flex items-center gap-0.5">
                                {props.children}
                                <ArrowUpRightIcon size={12}/>
                            </a>
                        }
                    }}
                >{content}</Markdown>
            </Tooltip.Content>
        </Tooltip>
    )
}