import React, {useEffect, useState} from "react";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import Section from "Frontend/components/general/Section";
import {ConfigEndpoint, UserEndpoint} from "Frontend/generated/endpoints";
import UserInfoDto from "Frontend/generated/de/grimsi/gameyfin/users/dto/UserInfoDto";
import {UserManagementCard} from "Frontend/components/general/UserManagementCard";
import {SmallInfoField} from "Frontend/components/general/SmallInfoField";
import {Info} from "@phosphor-icons/react";

function UserManagementLayout({getConfig, formik}: any) {
    const [users, setUsers] = useState<UserInfoDto[]>([]);
    const [autoRegisterNewUsers, setAutoRegisterNewUsers] = useState(true);

    useEffect(() => {
        UserEndpoint.getAllUsers().then(
            (response) => setUsers(response as UserInfoDto[])
        );

        ConfigEndpoint.get("sso.oidc.auto-register-new-users").then(
            (response) => setAutoRegisterNewUsers(response === "true")
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
            {!autoRegisterNewUsers &&
                <SmallInfoField className="mb-4 text-warning" icon={Info}
                                message="Automatic user registration for SSO users is disabled"/>
            }
            <div className="grid grid-cols-300px gap-4">
                {users.map((user) => <UserManagementCard user={user} key={user.username}/>)}
            </div>
        </div>
    );
}

export const UserManagement = withConfigPage(UserManagementLayout, "User Management", "users");