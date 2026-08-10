import {useAuth} from "Frontend/util/auth";
import {useEffect, useState} from "react";
import {Button, Card, Link, useOverlayState} from "@heroui/react";
import {Form, Formik} from "formik";
import Input from "Frontend/components/general/input/Input";
import PasswordResetModal from "Frontend/components/general/modals/PasswordResetModal";
import SignUpModal from "Frontend/components/general/modals/SignUpModal";
import {RegistrationEndpoint} from "Frontend/generated/endpoints";

export default function LoginView() {
    const {state, login} = useAuth();

    const passwordResetModal = useOverlayState();
    const signUpModal = useOverlayState();

    const [signUpAllowed, setSignUpAllowed] = useState<boolean>(false);

    useEffect(() => {
        if (state.user) {
            redirectAfterLogin();
        } else {
            RegistrationEndpoint.isSelfRegistrationAllowed().then(setSignUpAllowed);
        }
    }, [state.user]);

    async function tryLogin(values: any, formik: any) {
        const {defaultUrl, error, redirectUrl} = await login(values.username, values.password);
        if (error) {
            formik.setFieldError("username", " ");
            formik.setFieldError("password", "Invalid username and/or password.");
        } else {
            redirectAfterLogin(redirectUrl, defaultUrl);
        }
    }

    function redirectAfterLogin(redirectUrl?: string, defaultUrl?: string) {
        window.location.href = redirectUrl ?? defaultUrl ?? '/';
    }

    return (
        <div className="flex size-full gradient-primary">
            <Card className="m-auto p-12">
                <Card.Header>
                    <img
                        className="h-28 w-full content-center"
                        src="/images/Logo.svg"
                        alt="Gameyfin Logo"
                    />
                </Card.Header>
                <Card.Content className="mt-8 mb-2 w-80 max-w-(--breakpoint-lg) sm:w-96">
                    <Formik
                        initialValues={{}}
                        onSubmit={tryLogin}>
                        {(formik: { isSubmitting: any; }) => (
                            <Form className="mb-1 flex flex-col gap-6">
                                <Input
                                    name="username"
                                    label="Username"
                                    autoComplete="username"
                                />
                                <Input
                                    name="password"
                                    label="Password"
                                    autoComplete="current-password"
                                    type="password"
                                />
                                <div className="flex justify-between items-center">
                                    <Link href="#" className="text-foreground underline"
                                          onPress={passwordResetModal.open}>
                                        Forgot password?
                                    </Link>
                                    <div className="flex flex-row gap-2">
                                        {signUpAllowed &&
                                            <Button variant="tertiary" onPress={signUpModal.open}>
                                                Sign up
                                            </Button>
                                        }
                                        <Button variant="primary" type="submit" isPending={formik.isSubmitting}>
                                            {formik.isSubmitting ? "" : "Log in"}
                                        </Button>
                                    </div>
                                </div>
                            </Form>
                        )}
                    </Formik>
                </Card.Content>
            </Card>

            <PasswordResetModal isOpen={passwordResetModal.isOpen} onOpenChange={passwordResetModal.setOpen}/>
            <SignUpModal isOpen={signUpModal.isOpen} onOpenChange={signUpModal.setOpen}/>
        </div>
    );
}
