import {Card, Chip, Dropdown, DropdownItem, DropdownMenu, DropdownTrigger, useDisclosure} from "@nextui-org/react";
import {roleToColor, roleToRoleName} from "Frontend/util/utils";
import {DotsThreeVertical} from "@phosphor-icons/react";
import {useAuth} from "Frontend/util/auth";
import {useEffect, useState} from "react";
import {MessageEndpoint, PasswordResetEndpoint, RegistrationEndpoint} from "Frontend/generated/endpoints";
import {AvatarEndpoint} from "Frontend/endpoints/endpoints";
import Avatar from "Frontend/components/general/Avatar";
import ConfirmUserDeletionModal from "Frontend/components/general/ConfirmUserDeletionModal";
import PasswordResetTokenModal from "Frontend/components/general/PasswortResetTokenModal";
import TokenDto from "Frontend/generated/de/grimsi/gameyfin/shared/token/TokenDto";
import UserInfoDto from "Frontend/generated/de/grimsi/gameyfin/users/dto/UserInfoDto";

export function UserManagementCard({user}: { user: UserInfoDto }) {
    const userDeletionConfirmationModal = useDisclosure();
    const passwordResetTokenModal = useDisclosure();
    const [userEnabled, setUserEnabled] = useState(true);
    const [disabledKeys, setDisabledKeys] = useState<string[]>([]);
    const [dropdownItems, setDropdownItems] = useState<any[]>([]);
    const [passwordResetToken, setPasswordResetToken] = useState<TokenDto>();
    const auth = useAuth();

    useEffect(() => {
        setUserEnabled(user.enabled);
        let keysToBeDisabled: string[] = [];
        MessageEndpoint.isEnabled().then((isEnabled) => {
            if (isEnabled) keysToBeDisabled.push("resetPassword");
            if (!canUserBeDeleted()) keysToBeDisabled.push("delete")
            if (!user.hasAvatar) keysToBeDisabled.push("removeAvatar");
            setDisabledKeys(keysToBeDisabled);
        });
    }, []);

    useEffect(() => {
        setDropdownItems(getDropdownItems());
    }, [userEnabled]);

    function canUserBeDeleted(): Boolean {
        // User should not be able to delete himself through this menu (can be done via "My profile")
        if (auth.state.user?.username === user.username) return false;

        // User should not be able to delete the SUPERADMIN
        if (user.roles?.includes("ROLE_SUPERADMIN")) return false;

        // Superadmins can delete anyone excluding themselves (and other superadmins if there are any)
        if (auth.state.user?.roles?.includes("ROLE_SUPERADMIN")) return true;

        // Admins should be only allowed to delete other users, not other admins
        return !user.roles?.includes("ROLE_ADMIN");
    }

    async function resetPassword() {
        let token = await PasswordResetEndpoint.createPasswordResetTokenForUser(user.username);
        if (token === undefined) return;
        setPasswordResetToken(token);
        passwordResetTokenModal.onOpen();
    }

    function getDropdownItems() {
        let items = [];

        if (!user.enabled) {
            items.push(
                {
                    key: "enableUser",
                    onPress: () => {
                        RegistrationEndpoint.confirmRegistration(user.username).then(() => {
                            setUserEnabled(true);
                        })
                    },
                    label: "Enable user"
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
                key: "resetPassword",
                onPress: resetPassword,
                label: "Reset password"
            },
            {
                key: "delete",
                onPress: userDeletionConfirmationModal.onOpen,
                label: "Delete user"
            }
        );

        return items;
    }

    return (
        <>
            <Card className={`flex flex-row justify-between p-2 ${userEnabled ? "" : "bg-warning/25"}`}>
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
                        <p className="text-sm">{user.email}</p>
                        {user.roles?.map((role) =>
                            <Chip key={role} size="sm" radius="sm"
                                  className={`text-xs bg-${roleToColor(role!)}-500`}>{roleToRoleName(role!)}</Chip>)}
                    </div>
                </div>

                <Dropdown placement="bottom-end" size="sm" backdrop="opaque">
                    <DropdownTrigger>
                        <DotsThreeVertical/>
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
            </Card>
            <ConfirmUserDeletionModal isOpen={userDeletionConfirmationModal.isOpen}
                                      onOpenChange={userDeletionConfirmationModal.onOpenChange}
                                      user={user}/>
            <PasswordResetTokenModal isOpen={passwordResetTokenModal.isOpen}
                                     onOpenChange={passwordResetTokenModal.onOpenChange}
                                     token={passwordResetToken as TokenDto}/>
        </>
    )
}