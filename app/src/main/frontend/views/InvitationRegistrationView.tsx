import {toast, Button, Card, Spinner} from "@heroui/react";
import {useNavigate, useSearchParams} from "react-router";
import {Form, Formik} from "formik";
import Input from "Frontend/components/general/input/Input";
import * as Yup from "yup";
import {RegistrationEndpoint} from "Frontend/generated/endpoints";
import {useAuth} from "Frontend/util/auth";
import React, {useEffect, useState} from "react";
import {WarningIcon} from "@phosphor-icons/react";
import UserInvitationAcceptanceResult
    from "Frontend/generated/org/gameyfin/app/users/enums/UserInvitationAcceptanceResult";

export default function InvitationRegistrationView() {
    const {login} = useAuth();
    const [searchParams] = useSearchParams();
    const token = searchParams.get("token");
    const [invitation, setInvitation] = useState<{ token: string; email?: string; error?: string }>();
    const email = invitation?.token === token ? invitation.email : undefined;
    const error = !token ? "Invalid invitation token" : invitation?.token === token ? invitation.error : undefined;
    const navigate = useNavigate();

    useEffect(() => {
        if (!token) return;

        let active = true;
        setInvitation(undefined);
        RegistrationEndpoint.getInvitationRecipientEmail(token)
            .then(email => {
                if (active) setInvitation(email
                    ? {token, email}
                    : {token, error: "Invalid invitation token"});
            })
            .catch(error => {
                if (active) setInvitation({
                    token,
                    error: error instanceof Error ? error.message : "Unable to validate invitation"
                });
            });

        return () => { active = false; };
    }, [token]);

    async function register(values: any, formik: any) {
        if (!token || !email) return;

        let result = await RegistrationEndpoint.acceptInvitation(token, {
            email: email,
            username: values.username,
            password: values.password
        });

        switch (result) {
            case UserInvitationAcceptanceResult.SUCCESS: {
                toast.success("Registration successful", {
                    description: "Your account has been created",
                });
                const loginResult = await login(values.username, values.password).catch(() => undefined);
                if (!loginResult || loginResult.error) {
                    toast.warning("Automatic login failed", {
                        description: "Your account has been created. Please log in with your new credentials.",
                    });
                    navigate("/login", {replace: true});
                } else {
                    window.location.replace("/");
                }
                break;
            }
            case UserInvitationAcceptanceResult.USERNAME_TAKEN:
                formik.setFieldError("username", "Username is already taken");
                break;
            case UserInvitationAcceptanceResult.TOKEN_EXPIRED:
                setInvitation({token, error: "Invitation token has expired"});
                break;
            case UserInvitationAcceptanceResult.TOKEN_INVALID:
            default:
                setInvitation({token, error: "Invalid invitation token"});
                break;
        }
    }

    return (
        <div className="flex flex-row grow items-center justify-center size-full gradient-primary">
            <Card className="p-4 min-w-[468px]">
                <Card.Header className="mb-4">
                    <img
                        className="h-28 w-full content-center"
                        src="/images/Logo.svg"
                        alt="Gameyfin Logo"
                    />
                </Card.Header>
                <Card.Content>
                    {error ?
                        <p role="alert" className="flex flex-row grow justify-center items-center gap-2 text-danger text-2xl font-bold">
                            <WarningIcon weight="fill"/>
                            {error}
                        </p>
                        : email ?
                        <Formik
                            enableReinitialize={true}
                            initialValues={{
                                username: "",
                                email: email,
                                password: "",
                                passwordRepeat: ""
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
                            })}
                            onSubmit={register}>
                            {(formik: { isSubmitting: any; isValid: boolean; }) => (
                                <Form>
                                    <p className="text-xl text-center mb-8">Register a new account</p>
                                    <Input label="Email" name="email" type="email" value={email} isDisabled/>
                                    <Input label="Username" name="username" autoComplete="username"/>
                                    <Input label="Password" name="password" type="password"
                                           autoComplete="new-password"/>
                                    <Input label="Password (repeat)" name="passwordRepeat" type="password"
                                           autoComplete="new-password"/>
                                    <Button type="submit" className="w-full mt-4" variant="primary"
                                            isDisabled={!formik.isValid || formik.isSubmitting}
                                            isPending={formik.isSubmitting}>
                                        {formik.isSubmitting ? <Spinner size="sm" color="current"/> : "Create account"}
                                    </Button>
                                </Form>
                            )}
                        </Formik>
                        :
                        <div role="status" aria-label="Validating invitation" className="flex justify-center">
                            <Spinner/>
                        </div>
                    }
                </Card.Content>
            </Card>
        </div>
    );
}
