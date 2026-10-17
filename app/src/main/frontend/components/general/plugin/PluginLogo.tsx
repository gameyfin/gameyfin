import React from "react";
import PluginIcon from "Frontend/components/general/plugin/PluginIcon";
import PluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/PluginDto";

interface PluginLogoProps {
    plugin: PluginDto;
    glow?: boolean;
}

export default function PluginLogo({plugin, glow}: PluginLogoProps) {
    return <PluginIcon plugin={plugin} size={64} blurred={glow} showTooltip={false}/>
}