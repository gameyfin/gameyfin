import React from "react";
import {Divider} from "@heroui/react";
import {PluginManagementSection} from "Frontend/components/general/PluginManagementSection";

export default function PluginManagement() {

    // Defined manually for now to control the layout (order of categories)
    const pluginTypes = ["GameMetadataProvider", "DownloadProvider"];

    return (
        <div className="flex flex-col">
            <div className="flex flex-row flex-grow justify-between mb-8">
                <h2 className="text-2xl font-bold">Plugins</h2>
            </div>
            <Divider className="mb-4"/>

            <div className="flex flex-col gap-8">
                {pluginTypes.map(type =>
                    <PluginManagementSection key={type} pluginType={type}/>
                )}
            </div>
        </div>
    );
}