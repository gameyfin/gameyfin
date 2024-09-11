import React, {useEffect, useRef, useState} from "react";
import {ConfigController} from "Frontend/generated/endpoints";
import ConfigEntryDto from "Frontend/generated/de/grimsi/gameyfin/config/dto/ConfigEntryDto";
import {Form, Formik} from "formik";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import {Button, Divider, Skeleton} from "@nextui-org/react";
import {toast} from "sonner";
import {Check} from "@phosphor-icons/react";

type NestedConfig = {
    [field: string]: any;
}

type ConfigValuePair = {
    key: string;
    value: string | number | boolean | null | undefined;
}

export function LibraryManagement() {
    const isInitialized = useRef(false);
    const [configSaved, setConfigSaved] = useState(false);
    const [configDtos, setConfigDtos] = useState<ConfigEntryDto[]>([]);

    useEffect(() => {
        ConfigController.getConfigs("library").then((response: any) => {
            setConfigDtos(response as ConfigEntryDto[]);
            isInitialized.current = true;
        });
    }, []);

    useEffect(() => {
        if (configSaved) {
            setTimeout(() => setConfigSaved(false), 2000);
        }
    }, [configSaved])

    async function handleSubmit(values: NestedConfig) {
        const configValues = toConfigValuePair(values);
        await Promise.all(configValues.map(async (c: ConfigValuePair) => {
            if (c.value === null || c.value === undefined) {
                await ConfigController.deleteConfig(c.key);
                return;
            }

            await ConfigController.setConfig(c.key, c.value.toString());
        }));

        setConfigSaved(true);
        toast.success("Configuration saved");
    }

    function getConfig(key: string) {
        return configDtos.find((configDto: ConfigEntryDto) => configDto.key === key);
    }

    function toNestedConfig(configArray: ConfigEntryDto[]): NestedConfig {
        const nestedConfig: NestedConfig = {};

        configArray.forEach(item => {
            const keys = item.key!.split('.');
            let currentLevel = nestedConfig;

            // Traverse the nested structure and create objects as needed
            keys.forEach((key, index) => {
                if (index === keys.length - 1) {
                    // Convert value to the appropriate type
                    let value: any;
                    switch (item.type) {
                        case 'Boolean':
                            value = item.value === 'true';
                            break;
                        case 'Int':
                            value = parseInt(item.value!);
                            break;
                        case 'Float':
                            value = parseFloat(item.value!);
                            break;
                        case 'String':
                        default:
                            value = item.value;
                            break;
                    }
                    currentLevel[key] = value;
                } else {
                    if (!currentLevel[key]) {
                        currentLevel[key] = {};
                    }
                    currentLevel = currentLevel[key];
                }
            });
        });
        return nestedConfig;
    }

    function toConfigValuePair(obj: NestedConfig, parentKey: string = ''): ConfigValuePair[] {
        let result: ConfigValuePair[] = [];

        for (const key in obj) {
            if (obj.hasOwnProperty(key)) {
                const newKey = parentKey ? `${parentKey}.${key}` : key;
                if (typeof obj[key] === 'object' && !Array.isArray(obj[key])) {
                    result = result.concat(toConfigValuePair(obj[key], newKey));
                } else {
                    result.push({key: newKey, value: obj[key]});
                }
            }
        }

        return result;
    }

    if (!isInitialized.current) {
        return (
            [...Array(4)].map((e, i) =>
                <div className="flex flex-col flex-grow gap-8 mb-12" key={i}>
                    <Skeleton className="h-10 w-full rounded-md"/>
                    <Skeleton className="h-12 flex w-1/3 rounded-md"/>
                    <div className="flex flex-row gap-8">
                        <Skeleton className="h-12 flex w-1/3 rounded-md"/>
                        <Skeleton className="h-12 flex w-1/3 rounded-md"/>
                    </div>
                </div>
            )
        )
    }

    return (
        <Formik
            initialValues={toNestedConfig(configDtos)}
            onSubmit={handleSubmit}
        >
            {(formik: { values: any; isSubmitting: any; }) => (
                <Form>
                    <div className="flex flex-row flex-grow justify-between mb-8">
                        <h1 className="text-2xl font-bold">Library Management</h1>

                        <Button
                            color="secondary"
                            isLoading={formik.isSubmitting}
                            disabled={formik.isSubmitting || configSaved}
                            type="submit"
                        >
                            {formik.isSubmitting ? "" : configSaved ? <Check/> : "Save"}
                        </Button>
                    </div>
                    <div className="mb-8 flex flex-col flex-grow">

                        <ConfigFormField configElement={getConfig("library.allow-public-access")}></ConfigFormField>
                        <ConfigFormField
                            configElement={getConfig("library.scan.enable-filesystem-watcher")}></ConfigFormField>

                        <h2 className="text-xl font-bold mt-4">Metadata</h2>
                        <Divider/>
                        <div className="flex flex-row">
                            <ConfigFormField
                                configElement={getConfig("library.metadata.update.enabled")}></ConfigFormField>
                            <ConfigFormField
                                configElement={getConfig("library.metadata.update.schedule")}></ConfigFormField>
                        </div>

                        <ConfigFormField
                            configElement={getConfig("library.display.games-per-page")}></ConfigFormField>
                    </div>
                </Form>
            )}
        </Formik>
    );
}