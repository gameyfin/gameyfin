import React, {useEffect, useState} from "react";
import {Button, Modal} from "@heroui/react";
import TokenDto from "Frontend/generated/org/gameyfin/app/core/token/TokenDto";
import {timeUntil} from "Frontend/util/utils";
import CopyButton from "Frontend/components/general/CopyButton";

interface PasswordResetTokenModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    token: TokenDto;
}

export default function PasswordResetTokenModal({isOpen, onOpenChange, token}: PasswordResetTokenModalProps) {
    const [timeUntilExpiry, setTimeUntilExpiry] = useState<string>("");

    const timeoutRefresh = setInterval(updateTimeUntilExpiry, 1000);

    useEffect(updateTimeUntilExpiry, [token]);

    useEffect(() => {
        return () => {
            clearInterval(timeoutRefresh);
        };
    }, []);

    function passwordResetLink() {
        return `${document.baseURI}reset-password?token=${token.secret}`;
    }

    function updateTimeUntilExpiry() {
        if (!token) return;
        setTimeUntilExpiry(timeUntil(token.expiresAt as string));
    }

    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} isDismissable={false} variant="opaque">
                <Modal.Container size="lg" className="max-w-4xl">
                    <Modal.Dialog>
                        {({close}) => (
                            <>
                                <Modal.CloseTrigger/>
                                <Modal.Header className="flex flex-col gap-1">
                                    <Modal.Heading>
                                        Password Reset Link
                                    </Modal.Heading>
                                </Modal.Header>
                                <Modal.Body>
                                    <div className="flex items-center gap-2 rounded-lg bg-surface-secondary p-3">
                                        <pre className="m-0 flex-1 overflow-x-auto text-sm font-mono">
                                            <code>{passwordResetLink()}</code>
                                        </pre>
                                        <CopyButton text={passwordResetLink()} label="Copy password reset link"/>
                                    </div>
                                    {
                                        !timeUntilExpiry.endsWith("ago")
                                            ? <small className="text-warning">
                                                This link will expire {timeUntilExpiry}
                                            </small>
                                            : <small className="text-danger">
                                                This link has expired {timeUntilExpiry}
                                            </small>
                                    }
                                </Modal.Body>
                                <Modal.Footer>
                                    <Button variant="primary" onPress={close}>
                                        OK
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
