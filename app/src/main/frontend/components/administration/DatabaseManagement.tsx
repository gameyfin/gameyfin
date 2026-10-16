import React, {useEffect, useState} from "react";
import * as Yup from "yup";
import {Formik, FormikProps} from "formik";
import {Alert, Button, Skeleton, Spinner, toast, useOverlayState} from "@heroui/react";
import {ArrowsLeftRightIcon, PlugsConnectedIcon} from "@phosphor-icons/react";
import {DatabaseEndpoint} from "Frontend/generated/endpoints";
import DatabaseInfoDto from "Frontend/generated/org/gameyfin/app/core/database/DatabaseInfoDto";
import DatabaseType from "Frontend/generated/org/gameyfin/app/core/database/DatabaseType";
import DatabaseConnectionTestResultDto
    from "Frontend/generated/org/gameyfin/app/core/database/DatabaseConnectionTestResultDto";
import Section from "Frontend/components/general/Section";
import ConfirmModal from "Frontend/components/general/modals/ConfirmModal";
import {
    ConnectionTestResult,
    databaseFormValues,
    DatabaseFormValues,
    DatabaseSettingsFields,
    databaseTypeNames,
    databaseValidationSchema,
    isCurrentDatabase,
    isSameDatabase,
    toConnectionDto
} from "Frontend/components/general/database/DatabaseSettingsFields";
import {waitForRestart} from "Frontend/util/restart";

interface FormValues {
    database: DatabaseFormValues;
}

const validationSchema = Yup.object({database: databaseValidationSchema});

export default function DatabaseManagement() {
    const [info, setInfo] = useState<DatabaseInfoDto>();
    const [testing, setTesting] = useState(false);
    const [testResult, setTestResult] = useState<DatabaseConnectionTestResultDto>();
    const [switching, setSwitching] = useState(false);
    const confirmModal = useOverlayState();

    useEffect(() => {
        const handleVisibilityChange = () => {
            if (document.visibilityState === "hidden") {
                setTestResult(undefined);
            }
        };

        document.addEventListener("visibilitychange", handleVisibilityChange);

        return () => {
            document.removeEventListener("visibilitychange", handleVisibilityChange);
            setTestResult(undefined);
        };
    }, []);

    useEffect(() => {
        DatabaseEndpoint.getDatabaseInfo().then(setInfo);
    }, []);

    async function validate(formik: FormikProps<FormValues>): Promise<boolean> {
        const errors = await formik.validateForm();
        if (Object.keys(errors).length > 0) {
            await formik.setTouched({database: {host: true, port: true, database: true, username: true}});
            return false;
        }
        return true;
    }

    async function testConnection(formik: FormikProps<FormValues>): Promise<DatabaseConnectionTestResultDto | undefined> {
        if (!await validate(formik)) return undefined;
        setTesting(true);
        setTestResult(undefined);
        try {
            const result = await DatabaseEndpoint.testConnection(toConnectionDto(formik.values.database));
            setTestResult(result);
            return result;
        } catch (e: any) {
            toast.danger("Could not test connection", {description: e?.message});
            return undefined;
        } finally {
            setTesting(false);
        }
    }

    async function prepareSwitch(formik: FormikProps<FormValues>) {
        const result = await testConnection(formik);
        if (result?.success) confirmModal.open();
    }

    async function switchDatabase(values: DatabaseFormValues) {
        setSwitching(true);
        try {
            await DatabaseEndpoint.switchDatabase(toConnectionDto(values));
        } catch (e: any) {
            setSwitching(false);
            toast.danger("Could not switch database", {description: e?.message});
            return;
        }

        await waitForRestart();
        // All sessions are invalidated by the restart, so this will redirect to the login page
        window.location.reload();
    }

    return (
        <div className="flex flex-col">
            <Section title="Database"/>
            {!info ? <Skeleton className="h-24 w-full rounded-md"/> :
                <div className="flex flex-col gap-4">
                    <p className="text-sm">
                        Gameyfin is currently using
                        {" "}<b>{info.type ? databaseTypeNames[info.type] : "an unknown database"}</b>
                        {info.productName && ` (${info.productName} ${info.productVersion ?? ""})`.trimEnd()}
                        {info.type === DatabaseType.POSTGRESQL && info.host &&
                            <> at <code>{`${info.host}:${info.port}/${info.database}`}</code></>
                        }.
                    </p>

                    {info.lastSwitch &&
                        <Alert status={info.lastSwitch.success ? "success" : "danger"}>
                            <Alert.Indicator/>
                            <Alert.Content>
                                <Alert.Title className="font-bold">
                                    {info.lastSwitch.success ? "Last database switch succeeded" : "Last database switch failed"}
                                </Alert.Title>
                                <Alert.Description>
                                    {`${new Date(info.lastSwitch.finishedAt).toLocaleString()}: ${info.lastSwitch.message}`}
                                </Alert.Description>
                            </Alert.Content>
                        </Alert>
                    }

                    {info.managedExternally ?
                        <Alert status="accent">
                            <Alert.Indicator/>
                            <Alert.Content>
                                <Alert.Title className="font-bold">Managed by environment</Alert.Title>
                                <Alert.Description>
                                    The database is configured via environment variables
                                    (<code>SPRING_DATASOURCE_*</code>)
                                    {info.url && <> and uses <code>{info.url}</code></>}.
                                    To change the database in the UI, remove these variables and restart Gameyfin.
                                </Alert.Description>
                            </Alert.Content>
                        </Alert> :
                        <Formik<FormValues>
                            initialValues={{database: databaseFormValues(info)}}
                            validationSchema={validationSchema}
                            onSubmit={() => {
                                // Submitting is handled by the buttons, this component is nested in another form
                            }}
                        >
                            {(formik) => {
                                const values = formik.values.database;
                                const unchanged = isCurrentDatabase(values, info);
                                const onlyCredentials = isSameDatabase(values, info);
                                return (
                                    <div className="flex flex-col gap-4">
                                        <p className="text-sm text-muted">
                                            When switching to a different database, all data is transferred
                                            automatically: a backup of the current database is created and
                                            restored into the new database while Gameyfin restarts.<br/>
                                            The database connection settings are stored
                                            in <code>{info.settingsFile}</code>, make sure this file is persisted
                                            (e.g. mounted as a volume).
                                        </p>
                                        <DatabaseSettingsFields name="database"
                                                                passwordSet={info.passwordSet}
                                                                 isDisabled={switching}
                                                                 onTypeChange={() => setTestResult(undefined)}/>
                                        {testResult && <ConnectionTestResult result={testResult}/>}
                                        <div className="flex flex-row gap-4">
                                            <Button variant="ghost"
                                                    onPress={() => testConnection(formik)}
                                                    isPending={testing}
                                                    isDisabled={switching}>
                                                {testing ? <Spinner size="sm" color="current"/> :
                                                    <PlugsConnectedIcon/>}
                                                Test connection
                                            </Button>
                                            <Button onPress={() => prepareSwitch(formik)}
                                                    isDisabled={unchanged || testing || switching}>
                                                <ArrowsLeftRightIcon/>
                                                {onlyCredentials ? "Update credentials" : "Switch database"}
                                            </Button>
                                        </div>

                                        <ConfirmModal isOpen={confirmModal.isOpen}
                                                      onOpenChange={confirmModal.setOpen}
                                                      title={onlyCredentials ? "Update credentials" : "Switch database"}
                                                      confirmLabel={onlyCredentials ? "Update" : "Switch"}
                                                      isPending={switching}
                                                      onConfirm={() => switchDatabase(values)}>
                                            {onlyCredentials ?
                                                <p>
                                                    The new credentials are saved and Gameyfin restarts.
                                                    All users will be logged out.
                                                </p> :
                                                <>
                                                    <p>
                                                        Gameyfin will switch
                                                        to <b>{databaseTypeNames[values.type]}</b>.
                                                        A backup of the current database is created and restored into
                                                        the new database while Gameyfin restarts. All users will be
                                                        logged out.
                                                    </p>
                                                    {!!testResult?.existingTables &&
                                                        <p className="font-semibold text-danger">
                                                            The new database already contains
                                                            {" "}{testResult.existingTables} table(s). All existing data
                                                            in it will be deleted.
                                                        </p>
                                                    }
                                                    <p>
                                                        The current database is not modified, you can switch back
                                                        to it later.
                                                    </p>
                                                </>
                                            }
                                            {switching &&
                                                <p className="flex flex-row items-center gap-2 font-semibold">
                                                    <Spinner size="sm" color="current"/>
                                                    {onlyCredentials ? "Restarting, please wait..." :
                                                        "Transferring data, please wait..."}
                                                </p>
                                            }
                                        </ConfirmModal>
                                    </div>
                                );
                            }}
                        </Formik>
                    }
                </div>
            }
        </div>
    );
}
