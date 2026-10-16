import React from "react";
import * as Yup from "yup";
import {useField} from "formik";
import {Alert, Description, Label, Radio, RadioGroup} from "@heroui/react";
import Input from "Frontend/components/general/input/Input";
import NumberInput from "Frontend/components/general/input/NumberInput";
import DatabaseType from "Frontend/generated/org/gameyfin/app/core/database/DatabaseType";
import DatabaseConnectionDto from "Frontend/generated/org/gameyfin/app/core/database/DatabaseConnectionDto";
import DatabaseInfoDto from "Frontend/generated/org/gameyfin/app/core/database/DatabaseInfoDto";
import DatabaseConnectionTestResultDto
    from "Frontend/generated/org/gameyfin/app/core/database/DatabaseConnectionTestResultDto";

export interface DatabaseFormValues {
    type: DatabaseType;
    host: string;
    port: number;
    database: string;
    username: string;
    password: string;
}

export const DEFAULT_POSTGRESQL_PORT = 5432;

export const databaseTypeNames: Record<DatabaseType, string> = {
    [DatabaseType.H2]: "H2 (embedded)",
    [DatabaseType.POSTGRESQL]: "PostgreSQL"
};

export function databaseFormValues(info?: DatabaseInfoDto): DatabaseFormValues {
    return {
        type: info?.type ?? DatabaseType.H2,
        host: info?.host ?? "",
        port: info?.port ?? DEFAULT_POSTGRESQL_PORT,
        database: info?.database ?? "gameyfin_db",
        username: info?.username ?? "",
        password: ""
    };
}

function requiredForPostgres<T extends Yup.Schema>(schema: T) {
    return schema.when("type", {
        is: DatabaseType.POSTGRESQL,
        then: (s) => s.required("Required"),
        otherwise: (s) => s.notRequired()
    });
}

export const databaseValidationSchema = Yup.object({
    type: Yup.string().oneOf(Object.values(DatabaseType)).required(),
    host: requiredForPostgres(
        Yup.string().matches(/^(\[[0-9A-Fa-f:.]+]|[A-Za-z0-9][A-Za-z0-9._-]*)$/, "Invalid host name")
    ),
    port: requiredForPostgres(Yup.number().integer().min(1).max(65535)),
    database: requiredForPostgres(
        Yup.string().matches(/^[A-Za-z0-9_][A-Za-z0-9_.-]*$/, "Allowed are letters, digits, '_', '-' and '.'")
    ),
    username: requiredForPostgres(Yup.string()),
    password: Yup.string()
});

/** An empty password keeps the password of the currently configured database */
export function toConnectionDto(values: DatabaseFormValues): DatabaseConnectionDto {
    if (values.type === DatabaseType.H2) return {type: DatabaseType.H2};
    return {
        type: values.type,
        host: values.host.trim(),
        port: values.port,
        database: values.database.trim(),
        username: values.username.trim(),
        password: values.password === "" ? undefined : values.password
    };
}

/** Whether the values point to the same database as `info` (possibly with different credentials) */
export function isSameDatabase(values: DatabaseFormValues, info?: DatabaseInfoDto): boolean {
    if (!info || values.type !== info.type) return false;
    if (values.type === DatabaseType.H2) return true;
    return values.host.trim().toLowerCase() === info.host?.toLowerCase()
        && values.port === info.port
        && values.database.trim() === info.database;
}

/** Whether the values describe exactly the database (including credentials) described by `info` */
export function isCurrentDatabase(values: DatabaseFormValues, info?: DatabaseInfoDto): boolean {
    return isSameDatabase(values, info)
        && (values.type === DatabaseType.H2 || (values.username.trim() === info?.username && values.password === ""));
}

interface DatabaseSettingsFieldsProps {
    /** Name of the Formik field containing the `DatabaseFormValues` */
    name: string;
    /** Whether a password is stored for the current database (an empty password then keeps it) */
    passwordSet?: boolean;
    isDisabled?: boolean;
}

export function DatabaseSettingsFields({name, passwordSet, isDisabled}: DatabaseSettingsFieldsProps) {
    const [typeField, , typeHelpers] = useField<DatabaseType>(`${name}.type`);

    return (
        <div className="flex flex-col gap-4">
            <RadioGroup value={typeField.value}
                        onChange={(value) => typeHelpers.setValue(value as DatabaseType)}
                        isDisabled={isDisabled}
                        name={typeField.name}>
                <Label>Database</Label>
                <Radio value={DatabaseType.H2}>
                    <Radio.Content>
                        <Radio.Control>
                            <Radio.Indicator/>
                        </Radio.Control>
                        {databaseTypeNames[DatabaseType.H2]}
                    </Radio.Content>
                    <Description>
                        Stored in a file next to Gameyfin. No additional setup required, recommended for most users.
                    </Description>
                </Radio>
                <Radio value={DatabaseType.POSTGRESQL}>
                    <Radio.Content>
                        <Radio.Control>
                            <Radio.Indicator/>
                        </Radio.Control>
                        {databaseTypeNames[DatabaseType.POSTGRESQL]}
                    </Radio.Content>
                    <Description>Use an existing PostgreSQL server.</Description>
                </Radio>
            </RadioGroup>

            {typeField.value === DatabaseType.POSTGRESQL &&
                <div className="flex flex-col">
                    <div className="flex flex-row gap-4">
                        <Input label="Host" name={`${name}.host`} placeholder="e.g. localhost"
                               isDisabled={isDisabled}/>
                        <NumberInput label="Port" name={`${name}.port`} className="w-40"
                                     formatOptions={{useGrouping: false}}
                                     isDisabled={isDisabled}/>
                    </div>
                    <Input label="Database name" name={`${name}.database`} isDisabled={isDisabled}/>
                    <div className="flex flex-row gap-4">
                        <Input label="Username" name={`${name}.username`} autoComplete="off"
                               isDisabled={isDisabled}/>
                        <Input label="Password" name={`${name}.password`} type="password"
                               autoComplete="new-password"
                               placeholder={passwordSet ? "Leave empty to keep the current password" : undefined}
                               isDisabled={isDisabled}/>
                    </div>
                </div>
            }
        </div>
    );
}

export function ConnectionTestResult({result}: { result: DatabaseConnectionTestResultDto }) {
    const status = !result.success ? "danger" : result.existingTables > 0 ? "warning" : "success";
    return (
        <Alert status={status}>
            <Alert.Indicator/>
            <Alert.Content>
                <Alert.Title className="font-bold">
                    {result.success ? "Connection successful" : "Connection failed"}
                </Alert.Title>
                <Alert.Description>
                    {result.message}
                    {result.success && result.existingTables > 0 &&
                        <>
                            <br/>
                            The database already contains {result.existingTables} table(s).
                            All existing data in this database will be deleted when switching to it.
                        </>
                    }
                </Alert.Description>
            </Alert.Content>
        </Alert>
    );
}
