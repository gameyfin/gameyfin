import {Image, Tooltip} from "@heroui/react";
import {Plug} from "@phosphor-icons/react";
import PluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/PluginDto";

interface PluginIconProps {
    plugin: PluginDto;
    size?: number;
    blurred?: boolean;
    showTooltip?: boolean;
}

export default function PluginIcon({
                                       plugin,
                                       size = 16,
                                       blurred = false,
                                       showTooltip = true
                                   }: PluginIconProps) {

    const icon = plugin.hasLogo
        ?
        <Image isBlurred={blurred} src={`/images/plugins/${plugin.id}/logo`} width={size} height={size} radius="none"/>
        : <Plug size={size} weight="fill"/>;

    return showTooltip
        ? <Tooltip content={plugin.name}>{icon}</Tooltip>
        : icon;
}