import React, {useEffect, useRef, useState} from "react";
import {Button, toast, Tooltip} from "@heroui/react";
import {CheckIcon, CopyIcon} from "@phosphor-icons/react";

interface CopyButtonProps {
    text: string;
    label?: string;
}

export default function CopyButton({text, label = "Copy to clipboard"}: CopyButtonProps) {
    const [copied, setCopied] = useState(false);
    const timeout = useRef<ReturnType<typeof window.setTimeout> | undefined>(undefined);

    useEffect(() => {
        setCopied(false);
        return () => window.clearTimeout(timeout.current);
    }, [text]);

    async function copy() {
        try {
            await navigator.clipboard.writeText(text);
            window.clearTimeout(timeout.current);
            setCopied(true);
            timeout.current = window.setTimeout(() => setCopied(false), 5000);
        } catch {
            toast.warning("Could not copy. Select and copy the text manually.");
        }
    }

    return (
        <Tooltip isOpen={copied} delay={0}>
            <Button type="button" isIconOnly size="sm" variant="ghost" aria-label={label} onPress={copy}>
                <CopyIcon/>
            </Button>
            <Tooltip.Content placement="left" shouldFlip={false}>
                <span className="flex items-center gap-1 text-success" role="status">
                    Copied <CheckIcon aria-hidden="true"/>
                </span>
            </Tooltip.Content>
        </Tooltip>
    );
}
