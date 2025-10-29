import React from "react";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import Section from "Frontend/components/general/Section";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import * as Yup from "yup";
import {Alert, Button} from "@heroui/react";
import {FlaskIcon} from "@phosphor-icons/react";

function DownloadManagementLayout({getConfig, formik}: any) {
    return (
        <div className="flex flex-col">
            <Section title="Bandwidth limiting"/>
            <div className="flex flex-col gap-4">
                <Alert
                    title="Experimental Feature"
                    description="Bandwidth limiting is an experimental feature and may not work as expected. Please report any issues you encounter."
                    variant="solid"
                    hideIconWrapper={true}
                    icon={<FlaskIcon size={24}/>}
                    endContent={
                        <Button variant="flat"
                                className="bg-default-300"
                                onPress={() => window.open("https://github.com/gameyfin/gameyfin/issues", "_blank")}>
                            Open Issues
                        </Button>

                    }
                    classNames={{
                        title: "font-bold"
                    }}
                />
                <div className="flex flex-row items-baseline gap-4">
                    <ConfigFormField configElement={getConfig("downloads.bandwidth-limit.enabled")}/>
                    <ConfigFormField configElement={getConfig("downloads.bandwidth-limit.mbps")}
                                     isDisabled={!formik.values.downloads["bandwidth-limit"].enabled}/>
                </div>
            </div>
        </div>
    );
}

const validationSchema = Yup.object({
    downloads: Yup.object({
        "bandwidth-limit": Yup.object({
            enabled: Yup.boolean().required("Required"),
            mbps: Yup.number()
                .min(1, "Must be at least 1 Mbps")
                .required("Required"),
        }).required("Required")
    })
});
export const DownloadManagement = withConfigPage(DownloadManagementLayout, "Downloads", validationSchema);