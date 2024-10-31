import React, {useEffect, useState} from "react";
import {Button, Input, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader} from "@nextui-org/react";
import {RegistrationEndpoint, UserEndpoint} from "Frontend/generated/endpoints";
import {toast} from "sonner";

interface InviteUserModalProps {
    isOpen: boolean;
    onOpenChange: () => void;
}

export default function InviteUserModal({isOpen, onOpenChange}: InviteUserModalProps) {
    const [email, setEmail] = useState<string | null>();
    const [error, setError] = useState<string | null>();

    useEffect(() => {
        setEmail(null);
        setError(null);
    }, []);

    async function inviteUser(onClose: () => void) {
        if (email === null) return;

        if (await UserEndpoint.existsByMail(email)) {
            setError("User with this email already exists");
            return;
        }

        try {
            await RegistrationEndpoint.createInvitation(email);
            toast.success("Invitation has been sent");
            onClose();
        } catch (e) {
            setError("Failed to create invitation");
        }
    }

    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="opaque" size="lg">
            <ModalContent>
                {(onClose) => (
                    <>
                        <ModalHeader className="flex flex-col gap-1">Invite a new user</ModalHeader>
                        <ModalBody>
                            <p>Enter the email address of the user you want to invite:</p>
                            <Input errorMessage={error} onChange={(e) => setEmail(e.target.value)} type="email"/>
                            {error && <small className="text-danger">{error}</small>}
                        </ModalBody>
                        <ModalFooter>
                            <Button variant="light" onPress={onClose}>
                                Cancel
                            </Button>
                            <Button color="success" onPress={() => inviteUser(onClose)}
                                    isDisabled={email === null || email === undefined || email.length < 1}>
                                Send invitation
                            </Button>
                        </ModalFooter>
                    </>
                )}
            </ModalContent>
        </Modal>
    );
}