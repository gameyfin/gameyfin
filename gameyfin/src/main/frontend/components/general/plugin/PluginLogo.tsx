import {Plug} from "@phosphor-icons/react";
import React from "react";
import {Image} from "@heroui/react";
import PluginDto from "Frontend/generated/de/grimsi/gameyfin/core/plugins/dto/PluginDto";

interface PluginLogoProps {
    plugin: PluginDto;
}

export default function PluginLogo({plugin}: PluginLogoProps) {
    return (
        <>
            {plugin.hasLogo ?
                <Image isBlurred src={`/images/plugins/${plugin.id}/logo`} width={64} height={64} radius="none"/> :
                <Plug size={64} weight="fill"/>
            }
        </>
    );
}