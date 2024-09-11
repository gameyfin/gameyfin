import React from "react";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import Section from "Frontend/components/general/Section";
import * as Yup from "yup";

function UserManagementLayout({getConfig, formik}: any) {
    return (
        <div className="flex flex-col flex-grow">

            <Section title="Users"/>
            {/* TODO */}

            <Section title="Sign-Ups"/>
            <div className="flex flex-row">
                <ConfigFormField configElement={getConfig("users.sign-ups.allow")}/>
                <ConfigFormField configElement={getConfig("users.sign-ups.confirm")}
                                 isDisabled={!formik.values.users["sign-ups"].allow}/>
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

export const UserManagement = withConfigPage(UserManagementLayout, "User Management", "users", validationSchema);