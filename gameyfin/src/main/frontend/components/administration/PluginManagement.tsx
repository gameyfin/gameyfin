import React, {useEffect} from "react";
import {PluginManagementSection} from "Frontend/components/general/PluginManagementSection";
import {initializePluginState, pluginState} from "Frontend/state/PluginState";
import {useSnapshot} from "valtio/react";

export default function PluginManagement() {

    // Defined manually for now to control the layout (order of categories)
    const pluginTypes = ["GameMetadataProvider", "DownloadProvider"];

    const state = useSnapshot(pluginState);

    useEffect(() => {
        initializePluginState();
    }, []);

    return state.isLoaded && (
        <div className="flex flex-col">
            <div className="flex flex-row flex-grow justify-between mb-8">
                <h2 className="text-2xl font-bold">Plugins</h2>
            </div>

            <div className="flex flex-col gap-8">
                {pluginTypes.map(type =>
                    // @ts-ignore
                    <PluginManagementSection key={type} type={type} plugins={state.pluginsByType[type]}/>
                )}
            </div>
        </div>
    );
}