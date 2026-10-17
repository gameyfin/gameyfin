import React, {useEffect, useState} from "react";
import {Button, Modal} from "@heroui/react";
import TokenDto from "Frontend/generated/org/gameyfin/app/core/token/TokenDto";
import {timeUntil} from "Frontend/util/utils";
import {CopyIcon} from "@phosphor-icons/react";

interface PasswordResetTokenModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    token: TokenDto;
}

export default function PasswordResetTokenModal({isOpen, onOpenChange, token}: PasswordResetTokenModalProps) {
    const [timeUntilExpiry, setTimeUntilExpiry] = useState<string>("");
    const [copied, setCopied] = useState(false);

    const timeoutRefresh = setInterval(updateTimeUntilExpiry, 1000);

    useEffect(updateTimeUntilExpiry, [token]);
    useEffect(() => {
        setCopied(false);
    }, [isOpen, token]);

    useEffect(() => {
        return () => {
            clearInterval(timeoutRefresh);
        };
    }, []);

    function passwordResetLink() {
        return `${document.baseURI}reset-password?token=${token.secret}`;
    }

    async function copyPasswordResetLink() {
        await navigator.clipboard.writeText(passwordResetLink());
        setCopied(true);
        window.setTimeout(() => setCopied(false), 2000);
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
                                        The user can reset their password using the following link
                                    </Modal.Heading>
                                </Modal.Header>
                                <Modal.Body>
                                    <div className="flex items-start gap-2 rounded-lg bg-surface-secondary p-3">
                                        <pre className="m-0 flex-1 overflow-x-auto text-sm font-mono">
                                            <code>{passwordResetLink()}</code>
                                        </pre>
                                        <Button
                                            isIconOnly
                                            size="sm"
                                            variant="ghost"
                                            aria-label="Copy password reset link"
                                            onPress={copyPasswordResetLink}
                                        >
                                            <CopyIcon/>
                                        </Button>
                                    </div>
                                    {copied && (
                                        <small className="text-success">Password reset link copied.</small>
                                    )}
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