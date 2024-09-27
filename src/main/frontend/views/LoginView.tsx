import {useAuth} from "Frontend/util/auth";
import {useEffect, useState} from "react";
import {Button, Card, CardBody, CardHeader, Link, useDisclosure} from "@nextui-org/react";
import {useNavigate} from "react-router-dom";
import {Form, Formik} from "formik";
import Input from "Frontend/components/general/Input";
import PasswordResetModal from "Frontend/components/general/PasswordResetModal";
import SignUpModal from "Frontend/components/general/SignUpModal";
import {RegistrationEndpoint} from "Frontend/generated/endpoints";

export default function LoginView() {
    const {state, login} = useAuth();
    const navigate = useNavigate();

    const passwordResetModal = useDisclosure();
    const signUpModal = useDisclosure();

    const [url, setUrl] = useState<string>();
    const [signUpAllowed, setSignUpAllowed] = useState<boolean>(false);

    useEffect(() => {
        if (state.user) {
            const path = url ? new URL(url, document.baseURI).pathname : '/'
            navigate(path, {replace: true});
        } else {
            RegistrationEndpoint.isSelfRegistrationAllowed().then(setSignUpAllowed);
        }
    }, [state.user]);

    async function tryLogin(values: any, formik: any) {
        const {error} = await login(values.username, values.password);
        if (error) {
            formik.setFieldError("username", " "); // Mark the field red, but don't show an error message
            formik.setFieldError("password", "Invalid username and/or password.");
        }
    }

    return (
        <div className="flex size-full gradient-primary">
            <Card className="m-auto p-12">
                <CardHeader>
                    <img
                        className="h-28 w-full content-center"
                        src="/images/Logo.svg"
                        alt="Gameyfin Logo"
                    />
                </CardHeader>
                <CardBody className="mt-8 mb-2 w-80 max-w-screen-lg sm:w-96">
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
                                    <Link color="foreground" underline="always" href="#"
                                          onPress={passwordResetModal.onOpen}>
                                        Forgot password?
                                    </Link>
                                    <div className="flex flex-row gap-2">
                                        {signUpAllowed &&
                                            <Button color="default" variant="light"
                                                    onPress={signUpModal.onOpen}>
                                                Sign up
                                            </Button>
                                        }
                                        <Button color="primary" type="submit" isLoading={formik.isSubmitting}>
                                            {formik.isSubmitting ? "" : "Log in"}
                                        </Button>
                                    </div>
                                </div>
                            </Form>
                        )}
                    </Formik>
                </CardBody>
            </Card>

            <PasswordResetModal isOpen={passwordResetModal.isOpen} onOpenChange={passwordResetModal.onOpenChange}/>
            <SignUpModal isOpen={signUpModal.isOpen} onOpenChange={signUpModal.onOpenChange}/>
        </div>
    );
}