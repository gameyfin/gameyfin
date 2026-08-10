import {Tooltip} from "@heroui/react";
import { PlugIcon } from "@phosphor-icons/react";
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
        <img className={blurred ? "blur-sm" : undefined} src={`/images/plugins/${plugin.id}/logo`}
             width={size} height={size} alt={plugin.name}/>
        : <PlugIcon size={size} weight="fill"/>;

    return showTooltip
        ? <Tooltip>
            <Tooltip.Trigger>{icon}</Tooltip.Trigger>
            <Tooltip.Content>{plugin.name}</Tooltip.Content>
        </Tooltip>
        : icon;
}