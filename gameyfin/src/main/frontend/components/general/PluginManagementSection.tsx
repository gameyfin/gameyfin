import {Button, Tooltip, useDisclosure} from "@heroui/react";
import {ListNumbers} from "@phosphor-icons/react";
import {PluginManagementCard} from "Frontend/components/general/cards/PluginManagementCard";
import React, {useEffect, useState} from "react";
import PluginDto from "Frontend/generated/de/grimsi/gameyfin/core/plugins/management/PluginDto";
import {PluginManagementEndpoint} from "Frontend/generated/endpoints";
import PluginPrioritiesModal from "Frontend/components/general/modals/PluginPrioritiesModal";
import {camelCaseToTitle} from "Frontend/util/utils";

interface PluginManagementSectionProps {
    pluginType: string;
}

export function PluginManagementSection({pluginType}: PluginManagementSectionProps) {
    const [plugins, setPlugins] = useState<PluginDto[]>([]);
    const pluginPrioritiesModal = useDisclosure();

    useEffect(() => {
        PluginManagementEndpoint.getPlugins(pluginType).then((response) => {
            let sortedPlugins: PluginDto[] = response
                .filter(p => !!p)
                .sort((a: PluginDto, b: PluginDto) => {
                    if (a.name === undefined || b.name === undefined) return 0;
                    return a.name.localeCompare(b.name);
                });

            setPlugins(sortedPlugins);
        });
    }, []);

    function updatePlugin(plugin: PluginDto) {
        setPlugins(plugins.map(p => p.id === plugin.id ? plugin : p));
    }

    return (
        <div className="flex flex-col gap-2">
            <div className="flex flex-row flex-grow justify-between">
                <h2 className="text-xl font-bold">{camelCaseToTitle(pluginType)}</h2>

                <Tooltip color="foreground" placement="left" content="Change plugin order">
                    <Button isIconOnly variant="flat" onPress={pluginPrioritiesModal.onOpen}>
                        <ListNumbers/>
                    </Button>
                </Tooltip>
            </div>

            <div className="grid grid-cols-300px gap-4">
                {plugins.map((plugin) => <PluginManagementCard plugin={plugin}
                                                               updatePlugin={updatePlugin}
                                                               key={plugin.name}/>
                )}
            </div>

            <PluginPrioritiesModal
                key={plugins.map(p => p.id + p.priority).join(',')} // force re-mount if plugin order changes
                plugins={[...plugins].sort((a, b) => b.priority - a.priority)}
                isOpen={pluginPrioritiesModal.isOpen}
                onOpenChange={pluginPrioritiesModal.onOpenChange}
            />
        </div>);
}