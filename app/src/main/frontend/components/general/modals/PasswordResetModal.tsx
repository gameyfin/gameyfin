import React, {useEffect, useState} from "react";
import {Button, Input as NextInput, Modal, toast} from "@heroui/react";
import { WarningCircleIcon } from "@phosphor-icons/react";
import {MessageEndpoint, PasswordResetEndpoint} from "Frontend/generated/endpoints";

interface PasswordResetModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
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
        toast.success("Password reset requested", {
            description: "If the email address is registered, you will receive a message with further instructions."
        });
    }

    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
                <Modal.Container size="lg" className="max-w-xl">
                    <Modal.Dialog>
                        {({close}) => (
                            <>
                                <Modal.Header><Modal.Heading>Request a password reset</Modal.Heading></Modal.Header>
                                <Modal.Body>
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
                                </Modal.Body>
                                <Modal.Footer>
                                    <Button variant="danger-soft" onPress={close}>
                                        Cancel
                                    </Button>
                                    <Button variant="primary"
                                            isDisabled={!canResetPassword}
                                            onPress={async () => {
                                                await resetPassword();
                                                close();
                                            }}>
                                        Send request
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