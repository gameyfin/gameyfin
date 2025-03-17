import React from "react";
import {Button, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader} from "@heroui/react";
import {RegistrationEndpoint} from "Frontend/generated/endpoints";
import UserRegistrationDto from "Frontend/generated/de/grimsi/gameyfin/users/dto/UserRegistrationDto";
import {Form, Formik} from "formik";
import * as Yup from "yup";
import Input from "Frontend/components/general/Input";
import {toast} from "sonner";

interface SignUpModalProps {
    isOpen: boolean;
    onOpenChange: () => void;
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

            toast.success('You will receive an email with further instructions shortly.');
        } catch (_) {
            toast.error('An error occurred while registering your account.');
            return;
        }
    }

    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} size="xl">
            <ModalContent>
                {(onClose) => (
                    <Formik initialValues={{}}
                            onSubmit={async (values: any, {setFieldError}) => {
                                let usernameAvailable = await RegistrationEndpoint.isUsernameAvailable(values.username);
                                if (!usernameAvailable) {
                                    setFieldError('username', 'Username already taken');
                                    return;
                                } else {
                                    await signUp(values, onClose);
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
                            <ModalHeader className="flex flex-col gap-1">Register a new account</ModalHeader>
                            <ModalBody>
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
                            </ModalBody>
                            <ModalFooter>
                                <Button color="danger" variant="light" onPress={onClose}>
                                    Cancel
                                </Button>
                                <Button color="primary" type="submit">
                                    Create account
                                </Button>
                            </ModalFooter>
                        </Form>
                    </Formik>
                )}
            </ModalContent>
        </Modal>
    );
}