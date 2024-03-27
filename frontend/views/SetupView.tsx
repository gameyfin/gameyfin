import React from 'react';
import * as Yup from 'yup';
import Wizard from "Frontend/components/wizard/Wizard";
import WizardStep from "Frontend/components/wizard/WizardStep";
import Input from "Frontend/components/Input";
import {GearFine, HandWaving, Palette, User} from "@phosphor-icons/react";
import ThemePreview from "Frontend/components/theming/ThemePreview";
import {Theme, themes} from "Frontend/@/registry/themes";
import {Card} from "Frontend/@/components/ui/card";

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

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
        <div className="flex flex-col size-full items-center">
            <div className="size-full grid grid-cols-3 grid-rows-2 w-1/2 min-w-[468px] gap-12 items-center">
                {themes.map(((theme: Theme) => (
                    <ThemePreview key={theme.name} theme={theme}/>
                )))}
            </div>
        </div>
    )
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

const SetupView = () => (
    <div className="flex size-full bg-gradient-to-br from-gf-primary to-gf-secondary">
        <Card className="w-3/4 h-3/4 min-w-[500px] m-auto p-8">
            <Wizard
                initialValues={{username: '', password: '', passwordRepeat: ''}}
                onSubmit={async (values: any) =>
                    sleep(300).then(() => alert(JSON.stringify(values, null, 2)))
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

export default SetupView;