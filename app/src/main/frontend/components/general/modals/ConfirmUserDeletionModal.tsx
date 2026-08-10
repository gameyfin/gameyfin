import React, {useEffect, useState} from "react";
import {Button, Input, Modal} from "@heroui/react";
import {UserEndpoint} from "Frontend/generated/endpoints";
import UserInfoDto from "Frontend/generated/org/gameyfin/app/users/dto/UserInfoDto";

interface ConfirmUserDeletionModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
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
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} variant="opaque" isDismissable={false}>
                <Modal.Container size="lg">
                    <Modal.Dialog>
                        {({close}) => (
                            <>
                                <Modal.Header className="flex flex-col gap-1">
                                    <Modal.Heading>Confirm user deletion</Modal.Heading>
                                </Modal.Header>
                                <Modal.Body>
                                    <p>
                                        Confirm deletion of user{" "}
                                        <code className="px-2 py-1 h-fit font-mono font-normal inline-block whitespace-nowrap rounded-sm bg-default/40 text-default-foreground text-sm">
                                            {user.username}
                                        </code>{" "}
                                        by entering the username below
                                    </p>
                                    <Input onChange={(e) => setConfirmUsername(e.target.value)}/>
                                </Modal.Body>
                                <Modal.Footer>
                                    <Button variant="tertiary" onPress={close}>
                                        Cancel
                                    </Button>
                                    <Button
                                        variant="danger"
                                        onPress={deleteUser}
                                        isDisabled={confirmUsername != user.username}
                                    >
                                        Confirm deletion
                                    </Button>
                                </Modal.Footer>
                            </>
                        )}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}