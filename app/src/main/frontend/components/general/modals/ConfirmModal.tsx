import React from "react";
import {Button, Modal, Spinner} from "@heroui/react";

interface ConfirmModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    title: string;
    confirmLabel: string;
    isPending?: boolean;
    onConfirm: () => void;
    confirmVariant?: "danger" | "primary";
    children: React.ReactNode;
}

export default function ConfirmModal({
                                         isOpen,
                                         onOpenChange,
                                         title,
                                         confirmLabel,
                                         isPending,
                                         onConfirm,
                                         confirmVariant = "danger",
                                         children
                                     }: ConfirmModalProps) {
    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen}
                            onOpenChange={(open) => !isPending && onOpenChange(open)}
                            variant="opaque"
                            isDismissable={!isPending}>
                <Modal.Container size="lg">
                    <Modal.Dialog>
                        {({close}) => (
                            <>
                                <Modal.Header>
                                    <Modal.Heading>{title}</Modal.Heading>
                                </Modal.Header>
                                <Modal.Body className="flex flex-col gap-2">
                                    {children}
                                </Modal.Body>
                                <Modal.Footer>
                                    <Button variant="ghost" onPress={close} isDisabled={isPending}>
                                        Cancel
                                    </Button>
                                    <Button variant={confirmVariant} onPress={onConfirm} isPending={isPending}>
                                        {isPending && <Spinner size="sm" color="current"/>}
                                        {confirmLabel}
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
