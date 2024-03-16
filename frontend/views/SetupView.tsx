import React from 'react';
import {useFormikContext} from 'formik';
import * as Yup from 'yup';
import Wizard from "Frontend/components/wizard/Wizard";
import WizardStep from "Frontend/components/wizard/WizardStep";
import {Card, Typography} from "@material-tailwind/react";
import Input from "Frontend/components/Input";
import {GearFine, HandWaving, User} from "@phosphor-icons/react";

const sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms));

function WelcomeStep() {
    return (
        <div className="flex flex-col size-full gap-12 items-center">
            <Typography variant="h4">Welcome to Gameyfin 👋</Typography>
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
            <Typography variant="h5">Let's get started!</Typography>
        </div>
    );
}

function UserStep() {
    const formik = useFormikContext();
    return (
        <div className="flex flex-col size-full gap-12 items-center">
            <Typography variant="h4">Create your account</Typography>
            <Typography className="-mt-8">This will set up the initial admin user account.</Typography>
            <div className="mb-1 flex flex-col w-full gap-6">
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
    );
}

function SettingsStep() {
    return (
        <div className="flex flex-col items-center">
            <Typography variant="h4">Settings</Typography>
            <Typography>Configure your settings</Typography>
        </div>
    );
}

const SetupView = () => (
    <div className="flex h-screen">
        <div className="fixed size-full bg-gradient-to-br from-gf-primary to-gf-secondary"></div>
        <Card className="w-3/4 h-3/4 min-w-[500px] m-auto p-8" shadow={true}>
            <Wizard
                initialValues={{username: '', password: '', passwordRepeat: ''}}
                onSubmit={async (values: any) =>
                    sleep(300).then(() => alert(JSON.stringify(values, null, 2)))
                }
            >
                <WizardStep
                    // @ts-ignore
                    icon={<HandWaving/>}>
                    <WelcomeStep/>
                </WizardStep>
                <WizardStep
                    // @ts-ignore
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
                <WizardStep
                    //@ts-ignore
                    icon={<GearFine/>}
                >
                    <SettingsStep/>
                </WizardStep>
            </Wizard>
        </Card>
    </div>
);

export default SetupView;