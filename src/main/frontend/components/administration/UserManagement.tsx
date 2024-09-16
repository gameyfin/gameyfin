import React, {useEffect, useState} from "react";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import Section from "Frontend/components/general/Section";
import {UserEndpoint} from "Frontend/generated/endpoints";
import UserInfoDto from "Frontend/generated/de/grimsi/gameyfin/users/dto/UserInfoDto";
import {UserManagementCard} from "Frontend/components/general/UserManagementCard";

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
                {users.map((user) => <UserManagementCard user={user} key={user.username}/>)}
            </div>
        </div>
    );
}

export const UserManagement = withConfigPage(UserManagementLayout, "User Management", "users");