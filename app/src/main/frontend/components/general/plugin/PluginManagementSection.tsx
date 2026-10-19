import {Button, Tooltip, useOverlayState} from "@heroui/react";
import {ListNumbersIcon} from "@phosphor-icons/react";
import {PluginManagementCard} from "Frontend/components/general/cards/PluginManagementCard";
import React, {useEffect, useMemo} from "react";
import PluginPrioritiesModal from "Frontend/components/general/modals/PluginPrioritiesModal";
import {camelCaseToTitle} from "Frontend/util/utils";
import {useSnapshot} from "valtio/react";
import {pluginState} from "Frontend/state/PluginState";
import PluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/PluginDto";
import {initializeMarketplaceState} from "Frontend/state/MarketplaceState";

interface PluginManagementSectionProps {
    type: string;
    searchTerm?: string;
}

export function matchesSearchTerm(plugin: PluginDto, searchTerm?: string): boolean {
    const term = searchTerm?.trim().toLowerCase();
    if (!term) return true;

    return [plugin.name, plugin.id, plugin.author, plugin.shortDescription, plugin.description]
        .some(value => value?.toLowerCase().includes(term));
}

export function PluginManagementSection({type, searchTerm}: PluginManagementSectionProps) {
    const allPlugins = useSnapshot(pluginState).sortedByType[type];
    const plugins = useMemo(
        () => allPlugins.filter(plugin => matchesSearchTerm(plugin as PluginDto, searchTerm)),
        [allPlugins, searchTerm]
    );

    // Load marketplace data in the background, so available updates can be shown
    useEffect(() => {
        initializeMarketplaceState().catch(() => {
            // Errors are shown in the marketplace tab
        });
    }, []);

    const pluginPrioritiesModal = useOverlayState();

    return (
        <div className="flex flex-col gap-2">
            <div className="flex flex-row grow justify-between">
                <h2 className="text-xl font-bold">{camelCaseToTitle(type)}</h2>

                <Tooltip>
                    <Tooltip.Trigger>
                        <Button isIconOnly
                                variant="ghost"
                                onPress={pluginPrioritiesModal.open}
                                isDisabled={allPlugins.length === 0}>
                            <ListNumbersIcon/>
                        </Button>
                    </Tooltip.Trigger>
                    <Tooltip.Content placement="left">Change plugin order</Tooltip.Content>
                </Tooltip>
            </div>

            {plugins.length === 0 && <div className="flex flex-row justify-center">
                <p className="text-gray-500">
                    {allPlugins.length === 0 ? "No plugins of this type installed." : "No plugins match your search."}
                </p>
            </div>}

            {plugins.length > 0 && <div className="grid grid-cols-300px gap-4">
                {plugins.map((plugin) =>
                    <PluginManagementCard plugin={plugin} key={plugin.id}/>
                )}
            </div>}

            <PluginPrioritiesModal
                isOpen={pluginPrioritiesModal.isOpen}
                onOpenChange={pluginPrioritiesModal.setOpen}
                type={type}
            />
        </div>);
}