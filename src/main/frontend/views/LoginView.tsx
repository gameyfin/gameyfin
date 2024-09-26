import {useAuth} from "Frontend/util/auth";
import {useEffect, useState} from "react";
import {WarningCircle, XCircle} from "@phosphor-icons/react";
import {
    Button,
    Card,
    CardBody,
    CardHeader,
    Input,
    Link,
    Modal,
    ModalBody,
    ModalContent,
    ModalFooter,
    ModalHeader,
    useDisclosure
} from "@nextui-org/react";
import {Alert, AlertDescription, AlertTitle} from "Frontend/@/components/ui/alert";
import {useNavigate} from "react-router-dom";
import {MessageEndpoint, PasswordResetEndpoint} from "Frontend/generated/endpoints";
import {toast} from "sonner";

export default function LoginView() {
    const {state, login} = useAuth();
    const {isOpen, onOpen, onOpenChange} = useDisclosure();

    const [hasError, setError] = useState(false);
    const [loading, setLoading] = useState(false);
    const [username, setUsername] = useState<string>();
    const [password, setPassword] = useState<string>();
    const [canResetPassword, setCanResetPassword] = useState(false);
    const [url, setUrl] = useState<string>();
    const [resetEmail, setResetEmail] = useState<string>();

    const navigate = useNavigate();

    useEffect(() => {
        MessageEndpoint.isEnabled().then(setCanResetPassword);
    }, []);

    useEffect(() => {
        if (state.user) {
            const path = url ? new URL(url, document.baseURI).pathname : '/'
            navigate(path, {replace: true});
        }
    }, [state.user]);

    async function resetPassword() {
        await PasswordResetEndpoint.requestPasswordReset(resetEmail);
        toast.success("If the email address is registered, you will receive a message with further instructions.");
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
                    {hasError &&
                        <Alert className="mb-4" variant="destructive">
                            <XCircle weight="fill" className="size-4"/>
                            <AlertTitle>Error</AlertTitle>
                            <AlertDescription>Wrong username and/or password</AlertDescription>
                        </Alert>
                    }
                    <form
                        className="mb-1 flex flex-col gap-6"
                        onSubmit={async e => {
                            e.preventDefault();
                            if (typeof username === "string" && password != null) {
                                setLoading(true);
                                const {defaultUrl, error, redirectUrl} = await login(username, password);
                                if (error) {
                                    setError(true);
                                } else {
                                    setUrl(redirectUrl ?? defaultUrl ?? '/');
                                }
                                setLoading(false);
                            }
                        }}
                    >
                        <label htmlFor="username">
                            <h6 color="blue-gray" className="-mb-3">
                                Username
                            </h6>
                        </label>
                        <Input
                            onChange={(event: any) => {
                                setUsername(event.target.value);
                            }}
                            id="username"
                            type="text"
                            autoComplete="username"
                            placeholder=""
                        />
                        <label htmlFor="current-password">
                            <h6 color="blue-gray" className="-mb-3">
                                Password
                            </h6>
                        </label>
                        <Input
                            onChange={(event: any) => {
                                setPassword(event.target.value);
                            }}
                            id="current-password"
                            type="password"
                            autoComplete="current-password"
                            placeholder=""
                        />
                        <div className="flex justify-between items-center">
                            <Link color="foreground" underline="always" onPress={onOpen}>
                                Forgot password?
                            </Link>
                            <Button color="primary" type="submit" isLoading={loading}>
                                {loading ? "" : "Log in"}
                            </Button>
                        </div>
                    </form>
                </CardBody>
            </Card>

            <Modal isOpen={isOpen} onOpenChange={onOpenChange} size="xl">
                <ModalContent>
                    {(onClose) => (
                        <>
                            <ModalHeader className="flex flex-col gap-1">Request a password reset</ModalHeader>
                            <ModalBody>
                                {canResetPassword ?
                                    <Input
                                        onChange={(event: any) => {
                                            setResetEmail(event.target.value);
                                        }}
                                        type="email"
                                        placeholder="Email"
                                    /> :
                                    <div className="flex flex-row items-center gap-4 text-warning">
                                        <WarningCircle size={40}/>
                                        <p>
                                            Password self-service is disabled.<br/>
                                            To reset your password please contact your administrator.
                                        </p>
                                    </div>
                                }
                            </ModalBody>
                            <ModalFooter>
                                <Button color="danger" variant="light" onPress={onClose}>
                                    Cancel
                                </Button>
                                <Button color="primary"
                                        isDisabled={!canResetPassword}
                                        onPress={async () => {
                                            await resetPassword();
                                            onClose();
                                        }}>
                                    Send request
                                </Button>
                            </ModalFooter>
                        </>
                    )}
                </ModalContent>
            </Modal>
        </div>
    );
}