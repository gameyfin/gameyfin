import {Tooltip} from "@heroui/react";
import {PlugIcon} from "@phosphor-icons/react";
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
    const src = `/images/plugins/${plugin.id}/logo`;
    const icon = plugin.hasLogo
        ? <span className="relative inline-flex" style={{width: size, height: size}}>
            {blurred && <img className="absolute inset-0 size-full object-contain blur-lg opacity-70" src={src}
                             alt="" aria-hidden="true"/>}
            <img className="relative size-full object-contain" src={src} alt={plugin.name}/>
        </span>
        : <PlugIcon size={size} weight="fill"/>;

    return showTooltip
        ? <Tooltip>
            <Tooltip.Trigger>{icon}</Tooltip.Trigger>
            <Tooltip.Content>{plugin.name}</Tooltip.Content>
        </Tooltip>
        : icon;
}