import React, {useEffect, useState} from "react";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import Section from "Frontend/components/general/Section";
import {UserEndpoint} from "Frontend/generated/endpoints";
import {UserManagementCard} from "Frontend/components/general/cards/UserManagementCard";
import {UserPlusIcon} from "@phosphor-icons/react";
import {Button, Separator, Tooltip, useOverlayState} from "@heroui/react";
import InviteUserModal from "Frontend/components/general/modals/InviteUserModal";
import ExtendedUserInfoDto from "Frontend/generated/org/gameyfin/app/users/dto/ExtendedUserInfoDto";

function UserManagementLayout({getConfig, formik}: any) {
    const inviteUserModal = useOverlayState();
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
                <Tooltip delay={0}>
                    <Tooltip.Trigger>
                        <Button isIconOnly variant="tertiary" onPress={inviteUserModal.open}>
                            <UserPlusIcon/>
                        </Button>
                    </Tooltip.Trigger>
                    <Tooltip.Content>
                        <p>Invite new user</p>
                    </Tooltip.Content>
                </Tooltip>
            </div>
            <Separator className="mb-4"/>
            <div className="grid grid-cols-300px gap-4">
                {users.map((user) => <UserManagementCard user={user} key={user.username}/>)}
            </div>
            <InviteUserModal isOpen={inviteUserModal.isOpen} onOpenChange={inviteUserModal.toggle}/>
        </div>
    );
}

export const UserManagement = withConfigPage(UserManagementLayout, "User Management");