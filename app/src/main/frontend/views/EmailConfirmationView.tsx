import {Card, CardBody, CardHeader} from "@heroui/react";
import {useNavigate, useSearchParams} from "react-router";
import React, {useEffect, useState} from "react";
import {CheckCircle, Warning, WarningCircle} from "@phosphor-icons/react";
import TokenValidationResult from "Frontend/generated/org/gameyfin/app/shared/token/TokenValidationResult";
import {EmailConfirmationEndpoint} from "Frontend/generated/endpoints";
import {useAuth} from "Frontend/util/auth";

export default function EmailConfirmationView() {
    const auth = useAuth();
    const [searchParams, setSearchParams] = useSearchParams();
    const navigate = useNavigate();
    const [validationResult, setValidationResult] = useState<TokenValidationResult>(TokenValidationResult.INVALID);

    useEffect(() => {
        if (auth.state.user?.emailConfirmed === true) {
            navigate("/");
        }
    }, []);

    useEffect(() => {
        let token = searchParams.get("token");
        if (token) confirmEmail(token).then((result) => setValidationResult(result));
    }, [searchParams]);

    async function confirmEmail(token: string): Promise<TokenValidationResult> {
        let result = await EmailConfirmationEndpoint.confirmEmail(token) as TokenValidationResult;

        if (result === TokenValidationResult.VALID) {
            setTimeout(() => window.location.reload(), 5000);
        }

        return result;
    }

    return (
        <div className="flex flex-row grow items-center justify-center size-full gradient-primary">
            <Card className="p-4 min-w-[468px]">
                <CardHeader className="mb-4">
                    <img
                        className="h-28 w-full content-center"
                        src="/images/Logo.svg"
                        alt="Gameyfin Logo"
                    />
                </CardHeader>
                <CardBody className="flex flex-row justify-center">
                    {validationResult === TokenValidationResult.VALID ?
                        <div className="flex flex-row items-center gap-4 text-success">
                            <CheckCircle size={40}/>
                            <p>
                                Email confirmed<br/>
                                You will be redirected shortly
                            </p>
                        </div>
                        : validationResult === TokenValidationResult.EXPIRED ?
                            <div className="flex flex-row items-center gap-4 text-warning">
                                <WarningCircle size={40}/>
                                <p>
                                    Expired token<br/>
                                    Please request a new one
                                </p>
                            </div>
                            :
                            <div className="flex flex-row items-center gap-4 text-danger">
                                <Warning size={40}/>
                                <p>
                                    Invalid token<br/>
                                    Please try again
                                </p>
                            </div>
                    }
                </CardBody>
            </Card>
        </div>
    );
}