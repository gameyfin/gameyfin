import React from "react";
import {Button, Modal, toast} from "@heroui/react";
import {RegistrationEndpoint} from "Frontend/generated/endpoints";
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

    async function signUp(registration: UserRegistrationDto, onClose: () => void) {
        try {
            await RegistrationEndpoint.registerUser({
                username: registration.username,
                password: registration.password,
                email: registration.email
            });

            onClose();

            toast.success("Account created", {
                description: "You will receive an email with further instructions shortly."
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
                <Modal.Container size="lg" className="max-w-xl">
                    <Modal.Dialog>
                        {({close}) => (
                            <Formik initialValues={{}}
                                    onSubmit={async (values: any, {setFieldError}) => {
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
                                        email: Yup.string()
                                            .email()
                                            .required('Required'),
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
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}