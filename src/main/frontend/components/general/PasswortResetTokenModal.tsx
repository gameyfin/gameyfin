import React, {useEffect, useState} from "react";
import {Button, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader, Snippet} from "@nextui-org/react";
import TokenDto from "Frontend/generated/de/grimsi/gameyfin/shared/token/TokenDto";
import {timeUntil} from "Frontend/util/utils";

interface PasswordResetTokenModalProps {
    isOpen: boolean;
    onOpenChange: () => void;
    token: TokenDto;
}

export default function PasswordResetTokenModal({
                                                    isOpen,
                                                    onOpenChange,
                                                    token
                                                }: PasswordResetTokenModalProps) {
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
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} isDismissable={false}
               backdrop="opaque" size="4xl">
            <ModalContent>
                {(onClose) => (
                    <>
                        <ModalHeader className="flex flex-col gap-1">
                            The user can reset their password using the following link
                        </ModalHeader>
                        <ModalBody>
                            <Snippet symbol="">{passwordResetLink()}</Snippet>
                            {
                                !timeUntilExpiry.startsWith("-")
                                    ? <small className="text-warning">
                                        This link will expire in {timeUntilExpiry}
                                    </small>
                                    : <small className="text-danger">
                                        This link has expired {timeUntilExpiry.substring(1)} ago
                                    </small>
                            }
                        </ModalBody>
                        <ModalFooter>
                            <Button color="primary" onPress={onClose}>
                                OK
                            </Button>
                        </ModalFooter>
                    </>
                )}
            </ModalContent>
        </Modal>
    );
}