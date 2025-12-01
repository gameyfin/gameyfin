import React from "react";
import PluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/PluginDto";
import {PluginEndpoint} from "Frontend/generated/endpoints";
import PrioritiesModal from "./PrioritiesModal";
import {useSnapshot} from "valtio/react";
import {pluginState} from "Frontend/state/PluginState";

interface PluginPrioritiesModalProps {
    isOpen: boolean;
    onOpenChange: () => void;
    type: string;
}

export default function PluginPrioritiesModal({isOpen, onOpenChange, type}: PluginPrioritiesModalProps) {
    const plugins = useSnapshot(pluginState).sortedByType[type];

    const updatePlugins = async (reorderedPlugins: PluginDto[]) => {
        const prioritiesMap: Record<string, number> = {};
        const totalPlugins = reorderedPlugins.length;

        reorderedPlugins.forEach((plugin, index) => {
            // Reverse order: first item gets highest priority
            prioritiesMap[plugin.id] = totalPlugins - index;
        });

        await PluginEndpoint.setPluginPriorities(prioritiesMap);
    };

    return (
        <PrioritiesModal
            title="Edit plugin order"
            subtitle="Plugins higher on the list are preferred"
            items={plugins as PluginDto[]}
            updateItems={updatePlugins}
            isOpen={isOpen}
            onOpenChange={onOpenChange}
        />
    );
}