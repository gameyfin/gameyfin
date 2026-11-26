import ResponsiveDialog from "Frontend/components/layout/ResponsiveDialog";
import React, {useEffect, useId, useState} from "react";
import {Button, Modal, Surface, toast} from "@heroui/react";
import {MessageEndpoint, RegistrationEndpoint} from "Frontend/generated/endpoints";
import UserRegistrationDto from "Frontend/generated/org/gameyfin/app/users/dto/UserRegistrationDto";
import {Form, Formik} from "formik";
import * as Yup from "yup";
import Input from "Frontend/components/general/input/Input";
import GameyfinLogo from "Frontend/components/theming/GameyfinLogo";

interface SignUpModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
}

export default function SignUpModal({
                                        isOpen,
                                        onOpenChange
                                    }: SignUpModalProps) {

    const [emailRequired, setEmailRequired] = useState(true);
    const [messagesEnabled, setMessagesEnabled] = useState(false);
    const passwordHintId = useId();

    useEffect(() => {
        if (!isOpen) return;
        let active = true;
        RegistrationEndpoint.isEmailRequired().then(
            required => { if (active) setEmailRequired(required); },
            () => { if (active) setEmailRequired(true); }
        );
        MessageEndpoint.isEnabled().then(
            enabled => { if (active) setMessagesEnabled(enabled); },
            () => { if (active) setMessagesEnabled(false); }
        );
        return () => {
            active = false;
        };
    }, [isOpen]);

    async function signUp(registration: UserRegistrationDto, onClose: () => void) {
        try {
            await RegistrationEndpoint.registerUser({
                username: registration.username,
                password: registration.password,
                email: registration.email
            });

            onClose();

            toast.success("Registration submitted", {
                description: registration.email?.trim() && messagesEnabled
                    ? "Check your inbox for further instructions."
                    : "If approval is required, your administrator must activate your account."
            });
        } catch (_) {
            toast.danger("Registration failed", {
                description: "An error occurred while registering your account."
            });
            return;
        }
    }

    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
                <ResponsiveDialog size="md" placement="center">
                    <Modal.Dialog>
                        {({close}) => (
                            <Formik initialValues={{username: "", email: "", password: "", passwordRepeat: ""}}
                                    onSubmit={async (values, {setFieldError}) => {
                                        let usernameAvailable = await RegistrationEndpoint.isUsernameAvailable(values.username);
                                        if (!usernameAvailable) {
                                            setFieldError('username', 'Username already taken');
                                            return;
                                        } else {
                                            await signUp(values, close);
                                        }
                                    }}
                                    validationSchema={Yup.object({
                                        username: Yup.string()
                                            .required('Required'),
                                        password: Yup.string()
                                            .min(8, 'Password must be at least 8 characters long')
                                            .required('Required'),
                                        email: emailRequired
                                            ? Yup.string().trim().email().required('Required')
                                            : Yup.string().trim().email(),
                                        passwordRepeat: Yup.string()
                                            .equals([Yup.ref('password')], 'Passwords do not match')
                                            .required('Required')
                                    })}>
                                {({isSubmitting}) => <Form>
                                    <Modal.Body>
                                        <Modal.Header className="mb-4">
                                            <Surface variant="secondary"
                                                     className="flex w-full flex-col items-start gap-4 rounded-2xl bg-linear-to-br from-accent-soft to-surface-secondary p-5 sm:p-6">
                                                <span aria-hidden="true"
                                                      className="grid size-12 place-items-center rounded-xl bg-surface">
                                                    <GameyfinLogo className="size-7 fill-accent"/>
                                                </span>
                                                <div className="flex flex-col gap-1">
                                                    <Modal.Heading className="text-2xl font-semibold">
                                                        Create your Gameyfin account
                                                    </Modal.Heading>
                                                    <p className="text-sm text-muted">
                                                        Track what you play and build your own game collections.
                                                    </p>
                                                </div>
                                            </Surface>
                                        </Modal.Header>
                                        <div className="flex flex-col gap-1">
                                            <Input
                                                label="Username"
                                                name="username"
                                                type="text"
                                                autoComplete="username"
                                            />
                                            <Input
                                                label="Email"
                                                name="email"
                                                type="email"
                                                autoComplete="email"
                                                isRequired={emailRequired}
                                            />
                                            <p id={passwordHintId} className="mb-1 text-sm text-muted">
                                                Use at least 8 characters for your password.
                                            </p>
                                            <div className="grid gap-x-4 gap-y-1 sm:grid-cols-2">
                                                <Input
                                                    label="Password"
                                                    name="password"
                                                    type="password"
                                                    autoComplete="new-password"
                                                    aria-describedby={passwordHintId}
                                                />
                                                <Input
                                                    label="Confirm password"
                                                    name="passwordRepeat"
                                                    type="password"
                                                    autoComplete="new-password"
                                                />
                                            </div>
                                        </div>
                                    </Modal.Body>
                                    <Modal.Footer>
                                        <Button variant="tertiary" onPress={close}>
                                            Cancel
                                        </Button>
                                        <Button variant="primary" type="submit" isPending={isSubmitting}>
                                            Create account
                                        </Button>
                                    </Modal.Footer>
                                </Form>}
                            </Formik>
                        )}
                    </Modal.Dialog>
                </ResponsiveDialog>
            </Modal.Backdrop>
        </Modal>
    );
}
