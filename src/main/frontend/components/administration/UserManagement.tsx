import React, {useEffect, useState} from "react";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import Section from "Frontend/components/general/Section";
import * as Yup from "yup";
import {UserEndpoint} from "Frontend/generated/endpoints";
import UserInfoDto from "Frontend/generated/de/grimsi/gameyfin/users/dto/UserInfoDto";
import {UserCard} from "Frontend/components/general/UserCard";

function UserManagementLayout({getConfig, formik}: any) {
    const [users, setUsers] = useState<UserInfoDto[]>([]);

    useEffect(() => {
        UserEndpoint.getAllUsers().then(
            (response) => setUsers(response as UserInfoDto[])
        );
    }, []);

    return (
        <div className="flex flex-col flex-grow">

            <Section title="Sign-Ups"/>
            <div className="flex flex-row">
                <ConfigFormField configElement={getConfig("users.sign-ups.allow")}/>
                <ConfigFormField configElement={getConfig("users.sign-ups.confirm")}
                                 isDisabled={!formik.values.users["sign-ups"].allow}/>
            </div>

            <Section title="Users"/>
            <div className="grid grid-cols-300px gap-4">
                {users.map((user) => <UserCard user={user} key={user.username}/>)}
            </div>

        </div>
    )
        ;
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