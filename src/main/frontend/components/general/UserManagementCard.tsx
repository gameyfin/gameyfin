import UserInfoDto from "Frontend/generated/de/grimsi/gameyfin/users/dto/UserInfoDto";
import {
    Button,
    Card,
    Chip,
    Code,
    Dropdown,
    DropdownItem,
    DropdownMenu,
    DropdownTrigger,
    Input,
    Modal,
    ModalBody,
    ModalContent,
    ModalFooter,
    ModalHeader,
    useDisclosure
} from "@nextui-org/react";
import {roleToColor, roleToRoleName} from "Frontend/util/utils";
import {DotsThreeVertical} from "@phosphor-icons/react";
import {useAuth} from "Frontend/util/auth";
import {useEffect, useState} from "react";
import {UserEndpoint} from "Frontend/generated/endpoints";
import {AvatarEndpoint} from "Frontend/endpoints/endpoints";
import Avatar from "Frontend/components/general/Avatar";

export function UserManagementCard({user}: { user: UserInfoDto }) {
    const {isOpen, onOpen, onOpenChange} = useDisclosure();
    const [disabledKeys, setDisabledKeys] = useState<string[]>([]);
    const auth = useAuth();
    const [confirmUsername, setConfirmUsername] = useState<string>("");

    useEffect(() => {
        if (!canUserBeDeleted()) setDisabledKeys(["delete"])
    }, []);

    useEffect(() => {
        setConfirmUsername("");
    }, [isOpen]);

    function canUserBeDeleted(): Boolean {
        // User should not be able to delete himself through this menu (can be done via "My profile")
        if (auth.state.user?.username === user.username) return false;

        // User should not be able to delete the SUPERADMIN
        if (user.roles?.includes("ROLE_SUPERADMIN")) return false;

        // Superadmins can delete anyone excluding themselves (and other superadmins if there are any)
        if (auth.state.user?.roles?.includes("ROLE_SUPERADMIN")) return true;

        // Admins should be only allowed to delete other users, not other admins
        if (user.roles?.includes("ROLE_ADMIN")) return false;

        return true;
    }

    async function deleteUser() {
        await UserEndpoint.deleteUserByName(user.username);
        window.location.reload();
    }

    return (
        <Card className="flex flex-row justify-between p-2">
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
                <DropdownMenu aria-label="Static Actions" disabledKeys={disabledKeys}>
                    <DropdownItem key="removeAvatar" onPress={() => AvatarEndpoint.removeAvatarByName(user.username!)}>
                        Remove avatar
                    </DropdownItem>
                    <DropdownItem key="delete" className="text-danger" color="danger"
                                  onPress={onOpen}>
                        Delete user
                    </DropdownItem>
                </DropdownMenu>
            </Dropdown>

            <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="opaque" isDismissable={false}
                   hideCloseButton={true} size="lg">
                <ModalContent>
                    {(onClose) => (
                        <>
                            <ModalHeader className="flex flex-col gap-1">Confirm user deletion</ModalHeader>
                            <ModalBody>
                                <p>
                                    Confirm deletion of user <Code>{user.username}</Code> by entering the username
                                    below
                                </p>
                                <Input onChange={(e) => setConfirmUsername(e.target.value)}/>
                            </ModalBody>
                            <ModalFooter>
                                <Button variant="light" onPress={onClose}>
                                    Cancel
                                </Button>
                                <Button color="danger" onPress={deleteUser}
                                        isDisabled={confirmUsername != user.username}>
                                    Confirm deletion
                                </Button>
                            </ModalFooter>
                        </>
                    )}
                </ModalContent>
            </Modal>
        </Card>
    )
}