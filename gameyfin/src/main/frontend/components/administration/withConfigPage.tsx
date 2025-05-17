import React, {useEffect, useState} from "react";
import {ConfigEndpoint} from "Frontend/generated/endpoints";
import ConfigEntryDto from "Frontend/generated/de/grimsi/gameyfin/config/dto/ConfigEntryDto";
import {Form, Formik} from "formik";
import {Button, Skeleton} from "@heroui/react";
import {Check, Info} from "@phosphor-icons/react";
import {SmallInfoField} from "Frontend/components/general/SmallInfoField";
import {configState, initializeConfig, NestedConfig} from "Frontend/state/ConfigState";
import {useSnapshot} from "valtio/react";

export default function withConfigPage(WrappedComponent: React.ComponentType<any>, title: String, validationSchema?: any) {
    return function ConfigPage(props: any) {
        const [configSaved, setConfigSaved] = useState(false);
        const [saveMessage, setSaveMessage] = useState<string>();

        const state = useSnapshot(configState);

        useEffect(() => {
            initializeConfig();
        }, []);

        useEffect(() => {
            if (configSaved) {
                setTimeout(() => setConfigSaved(false), 2000);
            }
        }, [configSaved])

        async function handleSubmit(values: NestedConfig): Promise<void> {
            const changed = getChangedValues(state.configNested, values);
            await ConfigEndpoint.update({updates: changed});
            setConfigSaved(true);
        }

        function getConfig(key: string): ConfigEntryDto | undefined {
            // @ts-ignore
            return state.configEntries[key];
        }

        function getChangedValues(initial: NestedConfig, current: NestedConfig): Record<string, any> {
            const flatten = (obj: NestedConfig, parentKey = ''): Record<string, any> => {
                let result: Record<string, any> = {};
                for (const key in obj) {
                    if (obj.hasOwnProperty(key)) {
                        const newKey = parentKey ? `${parentKey}.${key}` : key;
                        if (typeof obj[key] === 'object' && obj[key] !== null && !Array.isArray(obj[key])) {
                            Object.assign(result, flatten(obj[key], newKey));
                        } else {
                            result[newKey] = obj[key];
                        }
                    }
                }
                return result;
            };

            const arraysEqual = (a: any[], b: any[]): boolean => {
                if (a.length !== b.length) return false;
                for (let i = 0; i < a.length; i++) {
                    if (Array.isArray(a[i]) && Array.isArray(b[i])) {
                        if (!arraysEqual(a[i], b[i])) return false;
                    } else if (a[i] !== b[i]) {
                        return false;
                    }
                }
                return true;
            };

            const flatInitial = flatten(initial);
            const flatCurrent = flatten(current);

            const changed: Record<string, any> = {};
            for (const key in flatCurrent) {
                const valA = flatCurrent[key];
                const valB = flatInitial[key];
                if (Array.isArray(valA) && Array.isArray(valB)) {
                    if (!arraysEqual(valA, valB)) {
                        changed[key] = valA;
                    }
                } else if (valA !== valB) {
                    changed[key] = valA;
                }
            }
            return changed;
        }

        return (
            <>
                {state.isLoaded ?
                    <Formik
                        initialValues={state.configNested}
                        onSubmit={handleSubmit}
                        validationSchema={validationSchema}
                        enableReinitialize={true}
                    >
                        {(formik) => (
                            <Form>
                                <div className="flex flex-row flex-grow justify-between">
                                    <h1 className="text-2xl font-bold">{title}</h1>

                                    <div className="flex flex-row items-center gap-4">
                                        {saveMessage && <SmallInfoField icon={Info}
                                                                        message={saveMessage}
                                                                        className="text-warning"/>}

                                        <Button
                                            color="primary"
                                            isLoading={formik.isSubmitting}
                                            isDisabled={formik.isSubmitting || configSaved || !formik.dirty}
                                            type="submit"
                                        >
                                            {formik.isSubmitting ? "" : configSaved ? <Check/> : "Save"}
                                        </Button>
                                    </div>
                                </div>

                                <WrappedComponent {...props}
                                                  getConfig={getConfig}
                                                  formik={formik}
                                                  setSaveMessage={setSaveMessage}/>
                            </Form>
                        )}
                    </Formik> :
                    [...Array(4)].map((_e, i) =>
                        <div className="flex flex-col flex-grow gap-8 mb-12" key={i}>
                            <Skeleton className="h-10 w-full rounded-md"/>
                            <Skeleton className="h-12 flex w-1/3 rounded-md"/>
                            <div className="flex flex-row gap-8">
                                <Skeleton className="h-12 flex w-1/3 rounded-md"/>
                                <Skeleton className="h-12 flex w-1/3 rounded-md"/>
                            </div>
                        </div>
                    )
                }
            </>
        );
    }
}