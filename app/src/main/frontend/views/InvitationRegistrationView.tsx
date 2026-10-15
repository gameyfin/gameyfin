import {toast, Button, Card} from "@heroui/react";
import {useNavigate, useSearchParams} from "react-router";
import {Form, Formik} from "formik";
import Input from "Frontend/components/general/input/Input";
import * as Yup from "yup";
import {RegistrationEndpoint} from "Frontend/generated/endpoints";
import React, {useEffect, useState} from "react";
import {WarningIcon} from "@phosphor-icons/react";
import UserInvitationAcceptanceResult
    from "Frontend/generated/org/gameyfin/app/users/enums/UserInvitationAcceptanceResult";

export default function InvitationRegistrationView() {
    const [searchParams] = useSearchParams();
    const [token, setToken] = useState<string>();
    const [email, setEmail] = useState<string>();
    const navigate = useNavigate();

    useEffect(() => {
        let token = searchParams.get("token");
        if (token) {
            setToken(token);
            RegistrationEndpoint.getInvitationRecipientEmail(token).then(setEmail);
        }
    }, [searchParams]);

    async function register(values: any, formik: any) {
        if (!token || !email) return;

        let result = await RegistrationEndpoint.acceptInvitation(token, {
            email: email,
            username: values.username,
            password: values.password
        });

        switch (result) {
            case UserInvitationAcceptanceResult.SUCCESS:
                toast.success("Registration successful", {
                    description: "Your account has been created",
                });
                navigate("/", {replace: true});
                break;
            case UserInvitationAcceptanceResult.USERNAME_TAKEN:
                formik.setFieldError("username", "Username is already taken");
                break;
            case UserInvitationAcceptanceResult.TOKEN_EXPIRED:
                toast.warning("Token expired", {
                    description: "Token is expired",
                });
                break;
            case UserInvitationAcceptanceResult.TOKEN_INVALID:
            default:
                toast.danger("Invalid token", {
                    description: "Token is invalid",
                });
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
                    {token ?
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
                                        {formik.isSubmitting ? "" : "Create account"}
                                    </Button>
                                </Form>
                            )}
                        </Formik>
                        :
                        <p className="flex flex-row grow justify-center items-center gap-2 text-danger text-2xl font-bold">
                            <WarningIcon weight="fill"/>
                            Invalid token
                        </p>
                    }
                </Card.Content>
            </Card>
        </div>
    );
}
