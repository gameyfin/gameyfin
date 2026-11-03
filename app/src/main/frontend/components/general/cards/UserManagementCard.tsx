import {Button, Card, Dropdown, Label, useOverlayState} from "@heroui/react";
import {DotsThreeVerticalIcon} from "@phosphor-icons/react";
import React, {useEffect, useState} from "react";
import {MessageEndpoint, PasswordResetEndpoint, UserEndpoint} from "Frontend/generated/endpoints";
import {AvatarEndpoint} from "Frontend/endpoints/endpoints";
import Avatar from "Frontend/components/general/Avatar";
import ConfirmUserDeletionModal from "Frontend/components/general/modals/ConfirmUserDeletionModal";
import PasswordResetTokenModal from "Frontend/components/general/modals/PasswortResetTokenModal";
import TokenDto from "Frontend/generated/org/gameyfin/app/core/token/TokenDto";
import RoleChip from "Frontend/components/general/RoleChip";
import AssignRolesModal from "Frontend/components/general/modals/AssignRolesModal";
import ExtendedUserInfoDto from "Frontend/generated/org/gameyfin/app/users/dto/ExtendedUserInfoDto";
import {useAuth} from "Frontend/util/auth";

export function UserManagementCard({user}: { user: ExtendedUserInfoDto }) {
    const userDeletionConfirmationModal = useOverlayState();
    const passwordResetTokenModal = useOverlayState();
    const roleAssignmentModal = useOverlayState();
    const auth = useAuth();
    const userEnabled = user.enabled;
    const [disabledKeys, setDisabledKeys] = useState<string[]>([]);
    const [passwordResetToken, setPasswordResetToken] = useState<TokenDto>();

    useEffect(() => {
        let cancelled = false;
        setDisabledKeys(["assignRole", "enableUser", "disableUser", "delete", "resetPassword", "removeAvatar"]);
        Promise.all([MessageEndpoint.isEnabled(), UserEndpoint.canCurrentUserManage(user.username)])
            .then(([messagesEnabled, canManage]) => {
                if (cancelled) return;
                const keys: string[] = [];
                if (messagesEnabled) keys.push("resetPassword");
                if (!user.hasAvatar) keys.push("removeAvatar");
                if (!canManage) keys.push("assignRole", "enableUser", "disableUser", "delete");
                setDisabledKeys(keys);
            }).catch(() => { /* Keep actions disabled when permissions could not be checked. */ });
        return () => { cancelled = true; };
    }, [user.username, user.hasAvatar, user.roles.join(","), auth.state.user?.roles.join(",")]);

    async function resetPassword() {
        let token = await PasswordResetEndpoint.createPasswordResetTokenForUser(user.username);
        if (token === undefined) return;
        setPasswordResetToken(token);
        passwordResetTokenModal.open();
    }

    function getDropdownItems() {
        let items = [];

        if (!user.managedBySso) {
            if (!userEnabled) {
                items.push(
                    {
                        key: "enableUser",
                        onPress: () => {
                            return UserEndpoint.setUserEnabled(user.username, true);
                        },
                        label: "Enable user"
                    }
                );
            } else {
                items.push(
                    {
                        key: "disableUser",
                        onPress: () => {
                            return UserEndpoint.setUserEnabled(user.username, false);
                        },
                        label: "Disable user"
                    }
                );
            }

            items.push(
                {
                    key: "removeAvatar",
                    onPress: () => AvatarEndpoint.removeAvatarByName(user.username!),
                    label: "Remove avatar"
                },
                {
                    key: "assignRole",
                    onPress: roleAssignmentModal.open,
                    label: "Assign role"
                },
                {
                    key: "resetPassword",
                    onPress: resetPassword,
                    label: "Reset password"
                }
            );
        }

        items.push({
                key: "delete",
                onPress: userDeletionConfirmationModal.open,
                label: "Delete user"
            }
        );

        return items;
    }

    const dropdownItems = getDropdownItems();

    function onDropdownAction(key: React.Key) {
        dropdownItems.find((item) => item.key === String(key))?.onPress();
    }

    return (
        <>
            <Card
                className={`flex flex-row justify-between p-2 ${userEnabled ? "" : "bg-warning/25"} ${user.managedBySso ? "text-foreground/50" : ""}`}>
                <div className="absolute right-0 top-0">
                    <Dropdown>
                        <Dropdown.Trigger>
                            <Button isIconOnly variant="ghost">
                                <DotsThreeVerticalIcon/>
                            </Button>
                        </Dropdown.Trigger>
                        <Dropdown.Popover placement="bottom end">
                            <Dropdown.Menu aria-label="Static Actions" disabledKeys={disabledKeys}
                                           onAction={onDropdownAction}>
                                {dropdownItems.map((item) => (
                                    <Dropdown.Item
                                        key={item.key}
                                        id={item.key}
                                        textValue={item.label}
                                        variant={item.key === "delete" ? "danger" : undefined}
                                    >
                                        <Label>{item.label}</Label>
                                    </Dropdown.Item>
                                ))}
                            </Dropdown.Menu>
                        </Dropdown.Popover>
                    </Dropdown>
                </div>
                <div className="flex flex-row items-center gap-4">
                    <Avatar username={user.username} hasAvatar={user.hasAvatar} avatarId={user.avatarId}
                            className="gradient-primary size-20 text-background/80 text-5xl"/>
                    <div className="flex flex-col gap-1">
                        <p className="font-semibold">{user.username}</p>
                        <p className="text-sm max-w-44 truncate" title={user.email}>{user.email}</p>
                        {user.roles?.map((role) => (
                            <RoleChip key={role} role={role as string}/>
                        ))}
                    </div>
                </div>
            </Card>
            <ConfirmUserDeletionModal isOpen={userDeletionConfirmationModal.isOpen}
                                      onOpenChange={userDeletionConfirmationModal.setOpen}
                                      user={user}/>
            <PasswordResetTokenModal isOpen={passwordResetTokenModal.isOpen}
                                     onOpenChange={passwordResetTokenModal.setOpen}
                                     token={passwordResetToken as TokenDto}/>
            <AssignRolesModal isOpen={roleAssignmentModal.isOpen} onOpenChange={roleAssignmentModal.setOpen}
                              user={user}/>
        </>
    )
}
