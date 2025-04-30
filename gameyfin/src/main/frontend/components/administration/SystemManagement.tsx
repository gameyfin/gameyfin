import React from "react";
import {SystemEndpoint} from "Frontend/generated/endpoints";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import {Button} from "@heroui/react";

function SystemManagementLayout() {
    return (
        <div className="flex flex-col mt-4">
            <Button onPress={() => SystemEndpoint.restart()}>Restart</Button>
        </div>
    );
}

export const SystemManagement = withConfigPage(SystemManagementLayout, "System", "system", null);