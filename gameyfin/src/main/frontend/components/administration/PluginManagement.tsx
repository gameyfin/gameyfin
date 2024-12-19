import React, {useEffect, useState} from "react";
import {PluginManagementEndpoint} from "Frontend/generated/endpoints";
import PluginDto from "Frontend/generated/de/grimsi/gameyfin/core/plugins/management/PluginDto";
import {PluginManagementCard} from "Frontend/components/general/PluginManagementCard";
import {Divider} from "@nextui-org/react";

export default function PluginManagement() {
    const [plugins, setPlugins] = useState<PluginDto[]>([]);

    useEffect(() => {
        PluginManagementEndpoint.getPlugins().then((response) => {
            if (response === undefined) return;
            setPlugins(response as PluginDto[]);
        });
    }, []);

    return (
        <div className="flex flex-col">
            <div className="flex flex-row flex-grow justify-between mb-8">
                <h2 className="text-2xl font-bold">Plugins</h2>
            </div>
            <Divider className="mb-4"/>

            <div className="flex flex-row flex-grow justify-between mb-8">
                <h2 className="text-xl font-bold">Metadata</h2>
            </div>

            <div className="grid grid-cols-300px gap-4">
                {plugins.map((plugin) => <PluginManagementCard plugin={plugin} key={plugin.name}/>)}
            </div>

            <div className="flex flex-row flex-grow justify-between my-8">
                <h2 className="text-xl font-bold">Notifications</h2>
            </div>
        </div>
    );
}