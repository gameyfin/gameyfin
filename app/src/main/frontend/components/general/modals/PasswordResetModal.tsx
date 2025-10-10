import React, {useEffect, useState} from "react";
import {addToast, Button, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader} from "@heroui/react";
import {Input as NextInput} from "@heroui/input";
import { WarningCircleIcon } from "@phosphor-icons/react";
import {MessageEndpoint, PasswordResetEndpoint} from "Frontend/generated/endpoints";

interface PasswordResetModalProps {
    isOpen: boolean;
    onOpenChange: () => void;
}

export default function PasswordResetModal({
                                               isOpen,
                                               onOpenChange
                                           }: PasswordResetModalProps) {
    const [canResetPassword, setCanResetPassword] = useState(false);
    const [resetEmail, setResetEmail] = useState<string>();

    useEffect(() => {
        MessageEndpoint.isEnabled().then(setCanResetPassword);
    }, []);

    async function resetPassword() {
        if (!resetEmail) return;

        await PasswordResetEndpoint.requestPasswordReset(resetEmail);
        addToast({
            title: "Password reset requested",
            description: "If the email address is registered, you will receive a message with further instructions.",
            color: "success"
        });
    }

    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} size="xl">
            <ModalContent>
                {(onClose) => (
                    <>
                        <ModalHeader className="flex flex-col gap-1">Request a password reset</ModalHeader>
                        <ModalBody>
                            {canResetPassword ?
                                <NextInput
                                    onChange={(event: any) => {
                                        setResetEmail(event.target.value);
                                    }}
                                    type="email"
                                    placeholder="Email"
                                /> :
                                <div className="flex flex-row items-center gap-4 text-warning">
                                    <WarningCircleIcon size={40}/>
                                    <p>
                                        Password self-service is disabled.<br/>
                                        To reset your password please contact your administrator.
                                    </p>
                                </div>
                            }
                        </ModalBody>
                        <ModalFooter>
                            <Button color="danger" variant="light" onPress={onClose}>
                                Cancel
                            </Button>
                            <Button color="primary"
                                    isDisabled={!canResetPassword}
                                    onPress={async () => {
                                        await resetPassword();
                                        onClose();
                                    }}>
                                Send request
                            </Button>
                        </ModalFooter>
                    </>
                )}
            </ModalContent>
        </Modal>
    );
}