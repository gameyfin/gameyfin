import React, {useEffect, useState} from 'react';
import * as Yup from 'yup';
import {useFormikContext} from "formik";
import Wizard from "Frontend/components/wizard/Wizard";
import WizardStep from "Frontend/components/wizard/WizardStep";
import Input from "Frontend/components/general/input/Input";
import CheckboxInput from "Frontend/components/general/input/CheckboxInput";
import {
    ArrowCounterClockwiseIcon,
    DatabaseIcon,
    HandWavingIcon,
    PaletteIcon,
    PlugsConnectedIcon,
    UserIcon
} from "@phosphor-icons/react";
import {Alert, Button, Card, Spinner, toast, useOverlayState} from "@heroui/react";
import {SetupEndpoint} from "Frontend/generated/endpoints";
import {ThemeSelector} from "Frontend/components/theming/ThemeSelector";
import {useNavigate} from "react-router";
import RestoreBackupModal from "Frontend/components/general/modals/RestoreBackupModal";
import DatabaseInfoDto from "Frontend/generated/org/gameyfin/app/core/database/DatabaseInfoDto";
import DatabaseConnectionTestResultDto
    from "Frontend/generated/org/gameyfin/app/core/database/DatabaseConnectionTestResultDto";
import {
    ConnectionTestResult,
    databaseFormValues,
    DatabaseSettingsFields,
    databaseTypeNames,
    databaseValidationSchema,
    isCurrentDatabase,
    toConnectionDto
} from "Frontend/components/general/database/DatabaseSettingsFields";
import {waitForRestart} from "Frontend/util/restart";

function WelcomeStep() {
    const navigate = useNavigate();
    const restoreModal = useOverlayState();

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
                <div className="flex flex-col items-center gap-2">
                    <p className="text-sm text-muted">Moving an existing Gameyfin instance?</p>
                    <Button variant="ghost" onPress={restoreModal.open}>
                        <ArrowCounterClockwiseIcon/>
                        Restore from backup
                    </Button>
                </div>
            </div>
            <RestoreBackupModal isOpen={restoreModal.isOpen}
                                onOpenChange={restoreModal.setOpen}
                                onRestored={() => {
                                    toast.success("Backup restored", {
                                        description: "You can now log in with your existing account.",
                                    });
                                    navigate('/login');
                                }}
            />
        </div>
    );
}

function ThemeStep() {
    return (
        <div className="flex flex-col grow gap-6 items-center">
            <p className="text-2xl font-bold">Choose your style</p>
            <ThemeSelector/>
        </div>
    );
}

interface DatabaseStepProps {
    info?: DatabaseInfoDto;
    testResult?: DatabaseConnectionTestResultDto;
    onTest: (values: any) => Promise<unknown>;
    onDatabaseTypeChange: () => void;
}

function DatabaseStep({info, testResult, onTest, onDatabaseTypeChange}: DatabaseStepProps) {
    const formik = useFormikContext<any>();
    const [testing, setTesting] = useState(false);

    async function test() {
        setTesting(true);
        try {
            await onTest(formik.values);
        } finally {
            setTesting(false);
        }
    }

    return (
        <div className="flex flex-row grow justify-center">
            <div className="flex flex-col w-1/2 min-w-96 gap-6">
                <p className="text-2xl font-bold text-center">Choose your database</p>
                {info?.managedExternally ?
                    <Alert status="accent">
                        <Alert.Indicator/>
                        <Alert.Content>
                            <Alert.Title className="font-bold">Managed by environment</Alert.Title>
                            <Alert.Description>
                                The database is configured via environment variables
                                {info.url && <> and uses <code>{info.url}</code></>}.
                            </Alert.Description>
                        </Alert.Content>
                    </Alert> :
                    <>
                        <p className="text-sm text-muted">
                            You can switch to a different database later in the administration area, all data is
                            transferred automatically.
                        </p>
                        <DatabaseSettingsFields name="database"
                                               onTypeChange={() => {
                                                   onDatabaseTypeChange();
                                                   formik.setFieldValue("overwriteDatabase", false);
                                               }}/>
                        {formik.values.database?.type !== info?.type &&
                            <div className="flex flex-col gap-4">
                                <Button variant="ghost" className="self-start" onPress={test} isPending={testing}>
                                    {testing ? <Spinner size="sm" color="current"/> : <PlugsConnectedIcon/>}
                                    Test connection
                                </Button>
                                {testResult && <ConnectionTestResult result={testResult}/>}
                                {!!testResult?.existingTables &&
                                    <CheckboxInput label="Delete all existing data in this database"
                                                   name="overwriteDatabase"/>
                                }
                            </div>
                        }
                    </>
                }
            </div>
        </div>
    );
}

function UserStep() {
    return (
        <div className="flex flex-row grow justify-center">
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
    const [databaseInfo, setDatabaseInfo] = useState<DatabaseInfoDto>();
    const [databaseTestResult, setDatabaseTestResult] = useState<DatabaseConnectionTestResultDto>();

    useEffect(() => {
        SetupEndpoint.getDatabaseInfo().then(setDatabaseInfo);
    }, []);

    /** Whether Gameyfin switches to the selected database after the setup */
    function switchesDatabase(values: any): boolean {
        return !!databaseInfo && !databaseInfo.managedExternally && !isCurrentDatabase(values.database, databaseInfo);
    }

    async function testDatabase(values: any): Promise<DatabaseConnectionTestResultDto | undefined> {
        setDatabaseTestResult(undefined);
        try {
            const result = await SetupEndpoint.testDatabaseConnection(toConnectionDto(values.database));
            setDatabaseTestResult(result);
            return result;
        } catch (e: any) {
            toast.danger("Could not test connection", {description: e?.message});
            return undefined;
        }
    }

    /** Prevents continuing with a database which is not reachable (or contains data which has not been confirmed to be deleted) */
    async function validateDatabase(values: any) {
        if (!switchesDatabase(values)) return;
        const result = await testDatabase(values);
        if (!result?.success) {
            throw new Error("Could not connect to the database");
        }
        if (result.existingTables > 0 && !values.overwriteDatabase) {
            toast.warning("The database is not empty", {
                description: "Confirm that all existing data in this database may be deleted."
            });
            throw new Error("Deleting existing data has not been confirmed");
        }
    }

    return (
        <div className="flex flex-row size-full items-center justify-center gradient-primary">
            <Card className="w-3/4 h-3/4 min-w-[500px] p-8">
                <Card.Content className="h-full">
                    <Wizard
                        initialValues={{
                            username: '',
                            email: '',
                            password: '',
                            passwordRepeat: '',
                            database: databaseFormValues(),
                            overwriteDatabase: false
                        }}
                        onSubmit={
                            async (values: any) => {
                                const database = switchesDatabase(values) ? toConnectionDto(values.database) : undefined;
                                try {
                                    await SetupEndpoint.registerSuperAdmin({
                                        username: values.username,
                                        password: values.password,
                                        email: values.email
                                    }, database);
                                } catch (e: any) {
                                    toast.warning("Could not complete the setup", {description: e?.message});
                                    // The database is validated before the user is created, so the setup can be retried
                                    if (!await SetupEndpoint.isSetupCompleted().catch(() => true)) return;
                                    navigate('/login');
                                    return;
                                }

                                if (database) {
                                    toast.info("Switching database", {
                                        description: `Gameyfin is restarting to transfer all data to ${databaseTypeNames[database.type]}. This may take a moment.`
                                    });
                                    await waitForRestart();
                                }
                                toast.success("Setup finished", {
                                    description: database
                                        ? "Have fun with Gameyfin! The result of the database switch is shown in the system settings."
                                        : "Have fun with Gameyfin!",
                                });
                                navigate('/login');
                            }
                        }
                    >
                        <WizardStep icon={<HandWavingIcon/>}>
                            <WelcomeStep/>
                        </WizardStep>
                        <WizardStep icon={<PaletteIcon/>}>
                            <ThemeStep/>
                        </WizardStep>
                        <WizardStep
                            validationSchema={Yup.object({database: databaseValidationSchema})}
                            onSubmit={validateDatabase}
                            icon={<DatabaseIcon/>}
                        >
                            <DatabaseStep info={databaseInfo}
                                          testResult={databaseTestResult}
                                           onTest={testDatabase}
                                           onDatabaseTypeChange={() => setDatabaseTestResult(undefined)}/>
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
                            icon={<UserIcon/>}
                        >
                            <UserStep/>
                        </WizardStep>
                    </Wizard>
                </Card.Content>
            </Card>
        </div>
    );
}

export default SetupView;
