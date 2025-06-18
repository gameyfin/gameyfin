import React from "react";
import {SystemEndpoint} from "Frontend/generated/endpoints";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import {Button} from "@heroui/react";
import Section from "Frontend/components/general/Section";

function SystemManagementLayout() {
    return (
        <div className="flex flex-col mt-4">
            <Section title="Restart Gameyfin"/>
            <Button onPress={() => SystemEndpoint.restart()}>Restart</Button>
        </div>
    );
}

export const SystemManagement = withConfigPage(SystemManagementLayout, "System");