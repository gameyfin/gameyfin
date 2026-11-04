import React from "react";
import {useSnapshot} from "valtio";
import {userState} from "Frontend/state/UserState";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import Section from "Frontend/components/general/Section";
import {UserManagementCard} from "Frontend/components/general/cards/UserManagementCard";
import {UserPlusIcon} from "@phosphor-icons/react";
import {Button, Separator, Tooltip, useOverlayState} from "@heroui/react";
import InviteUserModal from "Frontend/components/general/modals/InviteUserModal";

function UserManagementLayout({getConfig, formik}: any) {
    const inviteUserModal = useOverlayState();
    const {users} = useSnapshot(userState);

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
                        <Button isIconOnly variant="ghost" onPress={inviteUserModal.open}>
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
                {users.map((user) =>
                    <UserManagementCard
                        user={{...user, roles: [...user.roles]}} key={user.id}
                        canDelete={users.some(other => other.id !== user.id && other.roles.includes("ROLE_SUPERADMIN"))}
                    />
                )}
            </div>
            <InviteUserModal isOpen={inviteUserModal.isOpen} onOpenChange={inviteUserModal.toggle}/>
        </div>
    );
}

export const UserManagement = withConfigPage(UserManagementLayout, "User Management");