import React, {useEffect, useState} from "react";
import {Button, Modal, Spinner, toast} from "@heroui/react";
import {MessageEndpoint, RegistrationEndpoint, UserEndpoint} from "Frontend/generated/endpoints";
import TokenDto from "Frontend/generated/org/gameyfin/app/core/token/TokenDto";
import {Form, Formik, FormikErrors} from "formik";
import Input from "Frontend/components/general/input/Input";
import * as Yup from "yup";
import CopyButton from "Frontend/components/general/CopyButton";

interface InviteUserModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
}

export default function InviteUserModal({isOpen, onOpenChange}: InviteUserModalProps) {
    const [token, setToken] = useState<TokenDto | null>(null);
    const [isMessageServiceEnabled, setIsMessageServiceEnabled] = useState<boolean>(false);

    useEffect(() => {
        setToken(null);
        MessageEndpoint.isEnabled().then(enabled => {
            setIsMessageServiceEnabled(enabled);
        });
    }, [isOpen]);

    async function inviteUser(email: string, setErrors: (errors: FormikErrors<any>) => void, onClose: () => void) {
        if (!email) return;

        if (await UserEndpoint.existsByMail(email)) {
            setErrors({email: "User with this email already exists"});
            return;
        }

        if (!isMessageServiceEnabled) {
            let token = await RegistrationEndpoint.createInvitation(email);
            setToken(token);
            return;
        }

        await RegistrationEndpoint.createInvitation(email);
        toast("Invitation sent", {
            description: "The user will receive an email with further instructions shortly.",
            variant: "success"
        });
        onClose();
    }

    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} variant="opaque">
                <Modal.Container size="lg" className="max-w-4xl">
                    <Modal.Dialog>
                        {({close}) => {
                            const invitationLink = token
                                ? `${document.baseURI}accept-invitation?token=${token.secret}`
                                : "";

                            return (
                                <>
                                    <Modal.CloseTrigger/>
                                    <Formik
                                        initialValues={{email: ""}}
                                        isInitialValid={false}
                                        validationSchema={Yup.object({
                                            email: Yup.string()
                                                .email("Invalid email address")
                                                .required("Email is required")
                                        })}
                                        onSubmit={async (values: any, {setErrors}) => {
                                            await inviteUser(values.email, setErrors, close);
                                        }}
                                    >
                                        {(formik) => (
                                            <Form>
                                                <Modal.Header className="flex flex-col gap-1">
                                                    <Modal.Heading>Invite a new user</Modal.Heading>
                                                </Modal.Header>
                                                <Modal.Body>
                                                    <p>Enter the email address of the user you want to invite:</p>
                                                    <Input label="E-Mail" name="email" type="email"/>

                                                    {token && (
                                                        <div className="flex flex-col gap-2">
                                                            <p>The user can accept the invitation using the following
                                                                link:</p>
                                                            <div
                                                                className="flex items-center gap-2 rounded-lg bg-surface-secondary p-3">
                                                                <pre
                                                                    className="m-0 flex-1 overflow-x-auto text-sm font-mono">
                                                                    <code>{invitationLink}</code>
                                                                </pre>
                                                                <CopyButton text={invitationLink}
                                                                            label="Copy invitation link"/>
                                                            </div>
                                                        </div>
                                                    )}
                                                </Modal.Body>
                                                <Modal.Footer>
                                                    <Button variant="ghost" onPress={close}>
                                                        Cancel
                                                    </Button>
                                                    <Button
                                                        variant="primary"
                                                        type="submit"
                                                        isPending={formik.isSubmitting}
                                                        isDisabled={!formik.isValid || token !== null}
                                                    >
                                                        {formik.isSubmitting && <Spinner size="sm" color="current"/>}
                                                        {isMessageServiceEnabled ?
                                                            <p>Send invitation</p> :
                                                            <p>Generate invitation link</p>
                                                        }
                                                    </Button>
                                                </Modal.Footer>
                                            </Form>
                                        )}
                                    </Formik>
                                </>
                            );
                        }}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}
