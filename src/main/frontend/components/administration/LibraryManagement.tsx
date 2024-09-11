import React from "react";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import Section from "Frontend/components/general/Section";
import * as Yup from 'yup';

function LibraryManagementLayout({getConfig, formik}: any) {
    return (
        <div className="flex flex-col">

            <Section title="Library"/>
            {/* TODO */}

            <Section title="Permissions"/>
            <ConfigFormField configElement={getConfig("library.allow-public-access")}/>

            <Section title="Scanning"/>
            <ConfigFormField configElement={getConfig("library.scan.enable-filesystem-watcher")}/>

            <Section title="Metadata"/>
            <div className="flex flex-row">
                <ConfigFormField configElement={getConfig("library.metadata.update.enabled")}/>
                <ConfigFormField configElement={getConfig("library.metadata.update.schedule")}
                                 isDisabled={!formik.values.library.metadata.update.enabled}/>
            </div>
        </div>
    );
}

const validationSchema = Yup.object({
    library: Yup.object({
        metadata: Yup.object({
            update: Yup.object({
                // @ts-ignore
                schedule: Yup.string().cron()
            })
        })
    })
});

export const LibraryManagement = withConfigPage(LibraryManagementLayout, "Library Management", "library", validationSchema);