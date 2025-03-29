import React, {useEffect, useState} from "react";
import {PluginManagementEndpoint} from "Frontend/generated/endpoints";
import PluginDto from "Frontend/generated/de/grimsi/gameyfin/core/plugins/management/PluginDto";
import {PluginManagementCard} from "Frontend/components/general/PluginManagementCard";
import {Button, Divider, Tooltip, useDisclosure} from "@heroui/react";
import {ListNumbers} from "@phosphor-icons/react";
import PluginPrioritiesModal from "Frontend/components/general/PluginPrioritiesModal";

export default function PluginManagement() {
    const [plugins, setPlugins] = useState<PluginDto[]>([]);
    const pluginPrioritiesModal = useDisclosure();

    useEffect(() => {
        PluginManagementEndpoint.getPlugins().then((response) => {
            if (response === undefined) return;

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
        <div className="flex flex-col">
            <div className="flex flex-row flex-grow justify-between mb-8">
                <h2 className="text-2xl font-bold">Plugins</h2>
            </div>
            <Divider className="mb-4"/>

            <div className="flex flex-row flex-grow justify-between mb-8">
                <h2 className="text-xl font-bold">Metadata</h2>

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

            <div className="flex flex-row flex-grow justify-between my-8">
                <h2 className="text-xl font-bold">Notifications</h2>
            </div>
            <p>Notification plugins not yet supported.</p>

            <PluginPrioritiesModal plugins={plugins}
                                   isOpen={pluginPrioritiesModal.isOpen}
                                   onOpenChange={pluginPrioritiesModal.onOpenChange}
            />
        </div>
    );
}