import React, {useEffect, useState} from "react";
import {Button, Code, Input, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader} from "@nextui-org/react";
import {UserEndpoint} from "Frontend/generated/endpoints";
import UserInfoDto from "Frontend/generated/de/grimsi/gameyfin/users/dto/UserInfoDto";

interface ConfirmUserDeletionModalProps {
    isOpen: boolean;
    onOpenChange: () => void;
    user: UserInfoDto;
}

export default function ConfirmUserDeletionModal({isOpen, onOpenChange, user}: ConfirmUserDeletionModalProps) {
    const [confirmUsername, setConfirmUsername] = useState<string>("");

    useEffect(() => {
        setConfirmUsername("");
    }, []);

    async function deleteUser() {
        await UserEndpoint.deleteUserByName(user.username);
        window.location.reload();
    }

    return (
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
    );
}