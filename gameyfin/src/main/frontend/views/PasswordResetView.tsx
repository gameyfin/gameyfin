import {addToast, Button, Card, CardBody, CardHeader} from "@heroui/react";
import {useNavigate, useSearchParams} from "react-router";
import {Form, Formik} from "formik";
import Input from "Frontend/components/general/input/Input";
import * as Yup from "yup";
import {PasswordResetEndpoint} from "Frontend/generated/endpoints";
import React, {useEffect, useState} from "react";
import {Warning} from "@phosphor-icons/react";
import TokenValidationResult from "Frontend/generated/de/grimsi/gameyfin/shared/token/TokenValidationResult";

export default function PasswordResetView() {
    const [searchParams, setSearchParams] = useSearchParams();
    const [token, setToken] = useState<string>();
    const navigate = useNavigate();

    useEffect(() => {
        let token = searchParams.get("token");
        if (token) setToken(token);
    }, [searchParams]);

    async function resetPassword(values: any) {
        let token = searchParams.get("token") as string;
        let result = await PasswordResetEndpoint.resetPassword(token, values.password) as TokenValidationResult;

        switch (result) {
            case TokenValidationResult.VALID:
                addToast({
                    title: "Password reset",
                    description: "Password reset successfully",
                    color: "success"
                })
                navigate("/", {replace: true});
                break;
            case TokenValidationResult.EXPIRED:
                addToast({
                    title: "Token expired",
                    description: "Token is expired",
                    color: "warning"
                })
                break;
            case TokenValidationResult.INVALID:
            default:
                addToast({
                    title: "Invalid token",
                    description: "Token is invalid",
                    color: "danger"
                })
                break
        }
    }

    return (
        <div className="flex flex-row flex-grow items-center justify-center size-full gradient-primary">
            <Card className="p-4 min-w-[468px]">
                <CardHeader className="mb-4">
                    <img
                        className="h-28 w-full content-center"
                        src="/images/Logo.svg"
                        alt="Gameyfin Logo"
                    />
                </CardHeader>
                <CardBody>
                    {token ?
                        <Formik
                            initialValues={{
                                password: "",
                                passwordRepeat: ""
                            }}
                            validationSchema={Yup.object({
                                password: Yup.string()
                                    .min(8, 'Password must be at least 8 characters long')
                                    .required('Required'),
                                passwordRepeat: Yup.string()
                                    .equals([Yup.ref('password')], 'Passwords do not match')
                                    .required('Required')
                            })}
                            onSubmit={resetPassword}>
                            {(formik: { values: any; isSubmitting: any; isValid: boolean; }) => (
                                <Form>
                                    <p className="text-xl text-center mb-8">Reset your password</p>
                                    <Input label="Password" name="password" type="password"
                                           autoComplete="new-password"/>
                                    <Input label="Password (repeat)" name="passwordRepeat" type="password"
                                           autoComplete="new-password"/>
                                    <Button type="submit" className="w-full mt-4" color="primary"
                                            isDisabled={!formik.isValid || formik.isSubmitting}
                                            isLoading={formik.isSubmitting}>
                                        {formik.isSubmitting ? "" : "Reset password"}
                                    </Button>
                                </Form>
                            )}
                        </Formik>
                        :
                        <p className="flex flex-row flex-grow justify-center items-center gap-2 text-danger text-2xl font-bold">
                            <Warning weight="fill"/>
                            Invalid token
                        </p>
                    }
                </CardBody>
            </Card>
        </div>
    );
}