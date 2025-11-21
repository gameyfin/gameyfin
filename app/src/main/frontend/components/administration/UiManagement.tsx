import React from "react";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import Section from "Frontend/components/general/Section";

function UiManagementLayout({getConfig}: any) {

    return (
        <div className="flex flex-col mb-4">
            <Section title="Homepage"/>
            <ConfigFormField configElement={getConfig("ui.homepage.show-recently-added-games")}/>
        </div>
    );
}

export const UiManagement = withConfigPage(UiManagementLayout, "UI Settings");