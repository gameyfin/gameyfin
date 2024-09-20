import React, {useEffect, useRef, useState} from "react";
import {ConfigEndpoint} from "Frontend/generated/endpoints";
import ConfigEntryDto from "Frontend/generated/de/grimsi/gameyfin/config/dto/ConfigEntryDto";
import {Form, Formik} from "formik";
import {Button, Skeleton} from "@nextui-org/react";
import {Check, Info} from "@phosphor-icons/react";
import ConfigValuePairDto from "Frontend/generated/de/grimsi/gameyfin/config/dto/ConfigValuePairDto";
import {SmallInfoField} from "Frontend/components/general/SmallInfoField";

type NestedConfig = {
    [field: string]: any;
}

export default function withConfigPage(WrappedComponent: React.ComponentType<any>, title: String, configPrefix: string, validationSchema?: any) {
    return function ConfigPage(props: any) {
        const isInitialized = useRef(false);
        const [configSaved, setConfigSaved] = useState(false);
        const [configDtos, setConfigDtos] = useState<ConfigEntryDto[]>([]);
        const [nestedConfigDtos, setNestedConfigDtos] = useState<NestedConfig>({});
        const [saveMessage, setSaveMessage] = useState<string>();

        useEffect(() => {
            ConfigEndpoint.getAll(configPrefix).then((response: any) => {
                setConfigDtos(response as ConfigEntryDto[]);
                setNestedConfigDtos(toNestedConfig(response as ConfigEntryDto[]));
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
            await ConfigEndpoint.setAll(configValues);
            setNestedConfigDtos(values);
            setConfigSaved(true);
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

        function toConfigValuePair(obj: NestedConfig, parentKey: string = ''): ConfigValuePairDto[] {
            let result: ConfigValuePairDto[] = [];

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
                initialValues={nestedConfigDtos}
                onSubmit={handleSubmit}
                validationSchema={validationSchema}
                enableReinitialize={true}
            >
                {(formik) => (
                    <Form>
                        <div className="flex flex-row flex-grow justify-between mb-8">
                            <h1 className="text-2xl font-bold">{title}</h1>

                            <div className="flex flex-row items-center gap-4">
                                {saveMessage && <SmallInfoField icon={Info}
                                                                message={saveMessage}
                                                                className="text-warning"/>}

                                <Button
                                    color="primary"
                                    isLoading={formik.isSubmitting}
                                    disabled={formik.isSubmitting || configSaved || !formik.dirty}
                                    type="submit"
                                >
                                    {formik.isSubmitting ? "" : configSaved ? <Check/> : "Save"}
                                </Button>
                            </div>
                        </div>

                        <WrappedComponent {...props} getConfig={getConfig} formik={formik}
                                          setSaveMessage={setSaveMessage}/>
                    </Form>
                )}
            </Formik>
        );
    }
}