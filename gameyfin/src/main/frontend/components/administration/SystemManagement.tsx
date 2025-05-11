import React, {useEffect} from "react";
import {SystemEndpoint} from "Frontend/generated/endpoints";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import {Button} from "@heroui/react";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import Section from "Frontend/components/general/Section";

function SystemManagementLayout({getConfig, formik, setSaveMessage}: any) {

    useEffect(() => {
        if (formik.dirty && (formik.initialValues.system.cors["allowed-origins"] !== formik.values.system.cors["allowed-origins"])) {
            setSaveMessage("Gameyfin must be restarted for the changes to take effect");
        } else {
            setSaveMessage(null);
        }
    }, [formik.dirty]);

    return (
        <div className="flex flex-col mt-4">
            <Section title="Security configuration"/>
            <ConfigFormField configElement={getConfig("system.cors.allowed-origins")}/>

            <Section title="Restart Gameyfin"/>
            <Button onPress={() => SystemEndpoint.restart()}>Restart</Button>
        </div>
    );
}

export const SystemManagement = withConfigPage(SystemManagementLayout, "System", "system", null);