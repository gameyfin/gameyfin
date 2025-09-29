import React from "react";
import {SystemEndpoint} from "Frontend/generated/endpoints";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import {addToast, Button} from "@heroui/react";
import Section from "Frontend/components/general/Section";

function SystemManagementLayout() {

    function restart() {
        SystemEndpoint.restart().then(() =>
            addToast({
                title: "Restarting",
                description: "Gameyfin is restarting. This may take a few moments.",
                color: "success"
            })
        );
    }

    return (
        <div className="flex flex-col mt-4">
            <Section title="Restart Gameyfin"/>
            <Button onPress={restart}>Restart</Button>
        </div>
    );
}

export const SystemManagement = withConfigPage(SystemManagementLayout, "System");