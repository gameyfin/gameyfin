import React from 'react';
import * as Yup from 'yup';
import Wizard from "Frontend/components/wizard/Wizard";
import WizardStep from "Frontend/components/wizard/WizardStep";
import Input from "Frontend/components/general/Input";
import {HandWaving, Palette, User} from "@phosphor-icons/react";
import {addToast, Card} from "@heroui/react";
import {SetupEndpoint} from "Frontend/generated/endpoints";
import {ThemeSelector} from "Frontend/components/theming/ThemeSelector";
import {useNavigate} from "react-router-dom";

function WelcomeStep() {
    return (
        <div className="flex flex-col size-full items-center">
            <div className="flex flex-col w-1/2 min-w-[468px] gap-12 items-center">
                <h4>Welcome to Gameyfin 👋</h4>
                <p className="place-content-center text-justify">
                    Gameyfin is a cutting-edge software tailored for gamers seeking efficient management of their
                    video
                    game collections. <br/><br/> With its intuitive interface and comprehensive features, Gameyfin
                    simplifies the organization of game libraries. Users can effortlessly add games through manual
                    input
                    or
                    automated recognition, categorize them based on various criteria like genre or platform, track
                    in-game
                    progress, and share achievements with friends. <br/><br/> Notably, Gameyfin stands out for its
                    user-friendly
                    design and adaptability, offering ample customization options to meet diverse user preferences.
                </p>
                <h5>Let's get started!</h5>
            </div>
        </div>
    );
}

function ThemeStep() {
    return (
        <div className="flex flex-col flex-grow gap-6 items-center">
            <p className="text-2xl font-bold">Choose your style</p>
            <ThemeSelector/>
        </div>
    );
}

function UserStep() {
    return (
        <div className="flex flex-row flex-grow justify-center">
            <div className="flex flex-col w-1/3 min-w-96 gap-6 items-center">
                <p className="text-2xl font-bold">Create your account</p>
                <p>This will set up the initial admin user account.</p>
                <div className="flex flex-col w-full">
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
            </div>
        </div>
    );
}

function SetupView() {
    const navigate = useNavigate();

    return (
        <div className="flex flex-row size-full items-center justify-center gradient-primary">
            <Card className="w-3/4 h-3/4 min-w-[500px] p-8">
                <Wizard
                    initialValues={{username: '', email: '', password: '', passwordRepeat: ''}}
                    onSubmit={
                        async (values: any) => {
                            await SetupEndpoint.registerSuperAdmin({
                                username: values.username,
                                password: values.password,
                                email: values.email
                            });
                            addToast({
                                title: "Setup finished",
                                description: "Have fun with Gameyfin!",
                                color: "success"
                            })
                            navigate('/login');
                        }
                    }
                >
                    <WizardStep icon={<HandWaving/>}>
                        <WelcomeStep/>
                    </WizardStep>
                    <WizardStep icon={<Palette/>}>
                        <ThemeStep/>
                    </WizardStep>
                    <WizardStep
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
                        icon={<User/>}
                    >
                        <UserStep/>
                    </WizardStep>
                </Wizard>
            </Card>
        </div>
    );
}

export default SetupView;