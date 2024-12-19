import {Card, Chip, Skeleton, useDisclosure} from "@nextui-org/react";
import {PuzzlePiece} from "@phosphor-icons/react";
import {PluginManagementEndpoint} from "Frontend/generated/endpoints";
import PluginDto from "Frontend/generated/de/grimsi/gameyfin/core/plugins/management/PluginDto";
import PluginState from "Frontend/generated/org/pf4j/PluginState";
import React, {useEffect, useState} from "react";
import PluginDetailsModal from "Frontend/components/general/PluginDetailsModal";

export function PluginManagementCard({plugin}: { plugin: PluginDto }) {
    const pluginDetailsModal = useDisclosure();
    const [configValid, setConfigValid] = useState<boolean | undefined>(undefined);

    useEffect(() => {
        PluginManagementEndpoint.validatePluginConfig(plugin.id).then((response: boolean) => {
            if (response === undefined) return;
            setConfigValid(response);
        });
    }, []);

    function borderColor(state: PluginState | undefined): "success" | "warning" | "danger" | "default" {
        if (configValid === undefined) return "default";
        if (!configValid) return "danger";
        return stateToColor(state);
    }

    function stateToColor(state: PluginState | undefined): "success" | "warning" | "danger" | "default" {
        switch (state) {
            case PluginState.STARTED:
                return "success";
            case PluginState.DISABLED:
                return "warning";
            case PluginState.STOPPED:
                return "danger";
            default:
                return "default";
        }
    }

    return (
        <>
            <Card className={`flex flex-row justify-between p-2 border-2 border-${borderColor(plugin.state)}`}
                  isPressable={true} onPress={pluginDetailsModal.onOpen}>
                <div className="flex flex-1 flex-col items-center gap-1">
                    <PuzzlePiece size={64} weight="fill"/>
                    <p className="font-semibold">{plugin.name}</p>
                    <div className="flex flex-row gap-2">
                        <Chip size="sm" radius="sm" className="text-xs">{plugin.version}</Chip>
                        <Chip size="sm" radius="sm" className="text-xs"
                              color={stateToColor(plugin.state)}>{plugin.state?.toLowerCase()}</Chip>
                        {configValid === undefined ?
                            <Skeleton className="rounded-md h-6 w-20"></Skeleton>
                            : configValid ?
                                <Chip size="sm" radius="sm" className="text-xs" color="success">config valid</Chip> :
                                <Chip size="sm" radius="sm" className="text-xs" color="danger">config invalid</Chip>
                        }
                    </div>
                </div>
            </Card>
            <PluginDetailsModal plugin={plugin}
                                isOpen={pluginDetailsModal.isOpen}
                                onOpenChange={pluginDetailsModal.onOpenChange}
            />
        </>

    )
}