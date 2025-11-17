import {Button, Tooltip, useDisclosure} from "@heroui/react";
import { ListNumbersIcon } from "@phosphor-icons/react";
import {PluginManagementCard} from "Frontend/components/general/cards/PluginManagementCard";
import React from "react";
import PluginPrioritiesModal from "Frontend/components/general/modals/PluginPrioritiesModal";
import {camelCaseToTitle} from "Frontend/util/utils";
import PluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/PluginDto";

interface PluginManagementSectionProps {
    type: string;
    plugins: PluginDto[];
}

export function PluginManagementSection({type, plugins = []}: PluginManagementSectionProps) {
    const pluginPrioritiesModal = useDisclosure();

    return (
        <div className="flex flex-col gap-2">
            <div className="flex flex-row grow justify-between">
                <h2 className="text-xl font-bold">{camelCaseToTitle(type)}</h2>

                <Tooltip color="foreground" placement="left" content="Change plugin order">
                    <Button isIconOnly
                            variant="flat"
                            onPress={pluginPrioritiesModal.onOpen}
                            isDisabled={plugins.length === 0}>
                        <ListNumbersIcon/>
                    </Button>
                </Tooltip>
            </div>

            {plugins.length === 0 && <div className="flex flex-row justify-center">
                <p className="text-gray-500">No plugins of this type installed.</p>
            </div>}

            {plugins.length > 0 && <div className="grid grid-cols-300px gap-4">
                {plugins.map((plugin) =>
                    <PluginManagementCard plugin={plugin} key={plugin.id}/>
                )}
            </div>}

            <PluginPrioritiesModal
                key={plugins.map(p => p.id + p.priority).join(',')} // force re-mount if plugin order changes
                plugins={[...plugins].sort((a, b) => b.priority - a.priority)}
                isOpen={pluginPrioritiesModal.isOpen}
                onOpenChange={pluginPrioritiesModal.onOpenChange}
            />
        </div>);
}