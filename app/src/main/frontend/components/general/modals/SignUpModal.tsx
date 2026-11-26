import ResponsiveDialog from "Frontend/components/layout/ResponsiveDialog";
import React, {useEffect, useState} from "react";
import {Button, Modal, toast} from "@heroui/react";
import {MessageEndpoint, RegistrationEndpoint} from "Frontend/generated/endpoints";
import UserRegistrationDto from "Frontend/generated/org/gameyfin/app/users/dto/UserRegistrationDto";
import {Form, Formik} from "formik";
import * as Yup from "yup";
import Input from "Frontend/components/general/input/Input";

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
                <ResponsiveDialog size="lg" className="max-w-xl">
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
                                <Form>
                                    <Modal.Header><Modal.Heading>Register a new account</Modal.Heading></Modal.Header>
                                    <Modal.Body>
                                        <div className="flex flex-col">
                                            <Input
                                                label="Username"
                                                name="username"
                                                type="text"
                                            />
                                            <Input
                                                label="E-Mail"
                                                name="email"
                                                type="email"
                                                isRequired={emailRequired}
                                            />
                                            <Input
                                                label="Password"
                                                name="password"
                                                type="password"
                                            />
                                            <Input
                                                label="Password (repeat)"
                                                name="passwordRepeat"
                                                type="password"
                                            />
                                        </div>
                                    </Modal.Body>
                                    <Modal.Footer>
                                        <Button variant="danger-soft" onPress={close}>
                                            Cancel
                                        </Button>
                                        <Button variant="primary" type="submit">
                                            Create account
                                        </Button>
                                    </Modal.Footer>
                                </Form>
                            </Formik>
                        )}
                    </Modal.Dialog>
                </ResponsiveDialog>
            </Modal.Backdrop>
        </Modal>
    );
}