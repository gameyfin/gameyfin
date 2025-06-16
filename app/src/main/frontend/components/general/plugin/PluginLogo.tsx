import React from "react";
import PluginIcon from "Frontend/components/general/plugin/PluginIcon";
import PluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/PluginDto";

interface PluginLogoProps {
    plugin: PluginDto;
}

export default function PluginLogo({plugin}: PluginLogoProps) {
    return <PluginIcon plugin={plugin} size={64} blurred={true} showTooltip={false}/>
}