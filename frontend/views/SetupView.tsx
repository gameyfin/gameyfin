import React from 'react';
import * as Yup from 'yup';
import Wizard from "Frontend/components/wizard/Wizard";
import WizardStep from "Frontend/components/wizard/WizardStep";
import Input from "Frontend/components/Input";
import {GearFine, HandWaving, Palette, User} from "@phosphor-icons/react";
import {Card} from "@nextui-org/react";
import {SetupEndpoint} from "Frontend/generated/endpoints";
import {ThemeSelector} from "Frontend/components/theming/ThemeSelector";
import {useNavigate} from "react-router-dom";
import {toast} from "sonner";

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
        <ThemeSelector/>
    );
}

function UserStep() {
    return (
        <div className="flex flex-col size-full items-center">
            <div className="flex flex-col w-1/2 min-w-[468px] gap-12 items-center">
                <h4>Create your account</h4>
                <p className="-mt-8">This will set up the initial admin user account.</p>
                <div className="mb-1 flex flex-col w-full gap-6 items-center">
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

function SettingsStep() {
    return (
        <div className="flex flex-col size-full items-center">
            <div className="flex flex-col w-1/2 min-w-[468px] gap-12 items-center">
                <h4>Settings</h4>
                <p>Configure your settings</p>
            </div>
        </div>
    );
}

function SetupView() {
    const navigate = useNavigate();

    return (
        <div className="flex size-full gradient-primary">
            <Card className="w-3/4 h-3/4 min-w-[500px] m-auto p-8">
                <Wizard
                    initialValues={{username: '', email: '', password: '', passwordRepeat: ''}}
                    onSubmit={
                        async (values: any) => {
                            try {
                                await SetupEndpoint.registerSuperAdmin({
                                    username: values.username,
                                    password: values.password,
                                    email: values.email
                                });
                                toast("Setup finished", {description: "Have fun with Gameyfin!"});
                                navigate('/login');
                            } catch (e) {
                                alert("An error occurred while completing the setup. Please try again.")
                            }
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
                    <WizardStep icon={<GearFine/>}>
                        <SettingsStep/>
                    </WizardStep>
                </Wizard>
            </Card>
        </div>
    );
}

export default SetupView;