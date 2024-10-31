import {Card, Chip, Tooltip, useDisclosure} from "@nextui-org/react";
import {PuzzlePiece} from "@phosphor-icons/react";
import PluginDto from "Frontend/generated/de/grimsi/gameyfin/core/plugins/management/PluginDto";
import PluginState from "Frontend/generated/org/pf4j/PluginState";
import React from "react";
import PluginConfigurationModal from "Frontend/components/general/PluginConfigurationModal";

export function PluginManagementCard({plugin}: { plugin: PluginDto }) {
    const pluginConfigurationModal = useDisclosure();

    function stateToColor(state: PluginState | undefined): string {
        switch (state) {
            case PluginState.STARTED:
                return "success";
            case PluginState.DISABLED:
                return "warning";
            case PluginState.STOPPED:
                return "danger";
            default:
                return "";
        }
    }

    return (
        <>
            <Card className="flex flex-row justify-between p-2"
                  isPressable={true} onPress={pluginConfigurationModal.onOpen}>
                <div className="flex flex-row items-center gap-4">
                    <Tooltip placement="right" content={`Plugin ${plugin.state!.toLowerCase()}`}>
                        <PuzzlePiece size={64} weight="duotone" className={`text-${stateToColor(plugin.state)}`}/>
                    </Tooltip>
                    <div className="flex flex-col items-start gap-1">
                        <div className="flex flex-row gap-2">
                            <p className="font-semibold">{plugin.name}</p>
                            <div className="text-sm">
                                <Chip size="sm" radius="sm" className="text-xs">{plugin.version}</Chip>
                            </div>
                        </div>
                        <p className="text-sm">Author: {plugin.author}</p>
                    </div>
                </div>
            </Card>
            <PluginConfigurationModal plugin={plugin}
                                      isOpen={pluginConfigurationModal.isOpen}
                                      onOpenChange={pluginConfigurationModal.onOpenChange}
            />
        </>

    )
}