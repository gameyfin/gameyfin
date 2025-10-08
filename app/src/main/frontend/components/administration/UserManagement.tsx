import React, {useEffect, useState} from "react";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import Section from "Frontend/components/general/Section";
import {UserEndpoint} from "Frontend/generated/endpoints";
import {UserManagementCard} from "Frontend/components/general/cards/UserManagementCard";
import {SmallInfoField} from "Frontend/components/general/SmallInfoField";
import {Info, UserPlus} from "@phosphor-icons/react";
import {Button, Divider, Tooltip, useDisclosure} from "@heroui/react";
import InviteUserModal from "Frontend/components/general/modals/InviteUserModal";
import ExtendedUserInfoDto from "Frontend/generated/org/gameyfin/app/users/dto/ExtendedUserInfoDto";

function UserManagementLayout({getConfig, formik}: any) {
    const inviteUserModal = useDisclosure();
    const [users, setUsers] = useState<ExtendedUserInfoDto[]>([]);

    useEffect(() => {
        UserEndpoint.getAllUsers().then(
            (response) => setUsers(response)
        );
    }, []);

    return (
        <div className="flex flex-col grow">

            <Section title="Sign-Ups"/>
            <div className="flex flex-row">
                <ConfigFormField configElement={getConfig("users.sign-ups.allow")}/>
                <ConfigFormField configElement={getConfig("users.sign-ups.confirmation-required")}
                                 isDisabled={!formik.values.users["sign-ups"].allow}/>
            </div>

            <div className="flex flex-row items-baseline justify-between">
                <h2 className="text-xl font-bold mt-8 mb-1">Users</h2>
                {!getConfig("sso.oidc.auto-register-new-users").value &&
                    <SmallInfoField className="mb-4 text-warning" icon={Info}
                                    message="Automatic user registration for SSO users is disabled"/>
                }
                <Tooltip content="Invite new user">
                    <Button isIconOnly variant="flat" onPress={inviteUserModal.onOpen}>
                        <UserPlus/>
                    </Button>
                </Tooltip>
            </div>
            <Divider className="mb-4"/>
            <div className="grid grid-cols-300px gap-4">
                {users.map((user) => <UserManagementCard user={user} key={user.username}/>)}
            </div>
            <InviteUserModal isOpen={inviteUserModal.isOpen} onOpenChange={inviteUserModal.onOpenChange}/>
        </div>
    );
}

export const UserManagement = withConfigPage(UserManagementLayout, "User Management");