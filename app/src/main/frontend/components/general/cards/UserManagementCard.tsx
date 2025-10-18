import {Button, Card, Dropdown, DropdownItem, DropdownMenu, DropdownTrigger, useDisclosure} from "@heroui/react";
import { DotsThreeVerticalIcon } from "@phosphor-icons/react";
import React, {useEffect, useState} from "react";
import {MessageEndpoint, PasswordResetEndpoint, UserEndpoint} from "Frontend/generated/endpoints";
import {AvatarEndpoint} from "Frontend/endpoints/endpoints";
import Avatar from "Frontend/components/general/Avatar";
import ConfirmUserDeletionModal from "Frontend/components/general/modals/ConfirmUserDeletionModal";
import PasswordResetTokenModal from "Frontend/components/general/modals/PasswortResetTokenModal";
import TokenDto from "Frontend/generated/org/gameyfin/app/shared/token/TokenDto";
import RoleChip from "Frontend/components/general/RoleChip";
import AssignRolesModal from "Frontend/components/general/modals/AssignRolesModal";
import ExtendedUserInfoDto from "Frontend/generated/org/gameyfin/app/users/dto/ExtendedUserInfoDto";

export function UserManagementCard({user}: { user: ExtendedUserInfoDto }) {
    const userDeletionConfirmationModal = useDisclosure();
    const passwordResetTokenModal = useDisclosure();
    const roleAssignmentModal = useDisclosure();
    const [userEnabled, setUserEnabled] = useState(true);
    const [disabledKeys, setDisabledKeys] = useState<string[]>([]);
    const [dropdownItems, setDropdownItems] = useState<any[]>([]);
    const [passwordResetToken, setPasswordResetToken] = useState<TokenDto>();

    useEffect(() => {
        setUserEnabled(user.enabled);
        let keysToBeDisabled: string[] = [];
        MessageEndpoint.isEnabled().then((isEnabled) => {
            if (isEnabled) keysToBeDisabled.push("resetPassword");
            if (!user.hasAvatar) keysToBeDisabled.push("removeAvatar");
            setDisabledKeys(keysToBeDisabled);
        });
        UserEndpoint.canCurrentUserManage(user.username).then((canManage) => {
            if (!canManage) keysToBeDisabled.push("assignRole", "disableUser", "delete");
            setDisabledKeys(keysToBeDisabled);
        });
    }, []);

    useEffect(() => {
        setDropdownItems(getDropdownItems());
    }, [userEnabled]);

    async function resetPassword() {
        let token = await PasswordResetEndpoint.createPasswordResetTokenForUser(user.username);
        if (token === undefined) return;
        setPasswordResetToken(token);
        passwordResetTokenModal.onOpen();
    }

    function getDropdownItems() {
        let items = [];

        if (!user.managedBySso) {
            if (!userEnabled) {
                items.push(
                    {
                        key: "enableUser",
                        onPress: () => {
                            UserEndpoint.setUserEnabled(user.username, true).then(() => {
                                setUserEnabled(true);
                            })
                        },
                        label: "Enable user"
                    }
                );
            } else {
                items.push(
                    {
                        key: "disableUser",
                        onPress: () => {
                            UserEndpoint.setUserEnabled(user.username, false).then(() => {
                                setUserEnabled(false);
                            })
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
                    onPress: roleAssignmentModal.onOpen,
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
                onPress: userDeletionConfirmationModal.onOpen,
                label: "Delete user"
            }
        );

        return items;
    }

    return (
        <>
            <Card
                className={`flex flex-row justify-between p-2 ${userEnabled ? "" : "bg-warning/25"} ${user.managedBySso ? "text-foreground/50" : ""}`}>
                <div className="absolute right-0 top-0">
                    <Dropdown placement="bottom-end" size="sm" backdrop="opaque">
                        <DropdownTrigger>
                            <Button isIconOnly variant="light">
                                <DotsThreeVerticalIcon/>
                            </Button>
                        </DropdownTrigger>
                        <DropdownMenu aria-label="Static Actions" items={dropdownItems} disabledKeys={disabledKeys}>
                            {(item) => (
                                <DropdownItem
                                    key={item.key}
                                    onPress={item.onPress}
                                    color={item.key === "delete" ? "danger" : "default"}
                                    className={item.key === "delete" ? "text-danger" : ""}
                                >
                                    {item.label}
                                </DropdownItem>
                            )}
                        </DropdownMenu>
                    </Dropdown>
                </div>
                <div className="flex flex-row items-center gap-4">
                    <Avatar username={user.username}
                            name={user.username?.charAt(0)}
                            classNames={{
                                base: "gradient-primary size-20",
                                icon: "text-background/80",
                                name: "text-background/80 text-5xl",
                            }}/>
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
                                      onOpenChange={userDeletionConfirmationModal.onOpenChange}
                                      user={user}/>
            <PasswordResetTokenModal isOpen={passwordResetTokenModal.isOpen}
                                     onOpenChange={passwordResetTokenModal.onOpenChange}
                                     token={passwordResetToken as TokenDto}/>
            <AssignRolesModal isOpen={roleAssignmentModal.isOpen} onOpenChange={roleAssignmentModal.onOpenChange}
                              user={user}/>
        </>
    )
}