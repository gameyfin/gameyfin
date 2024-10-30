import React, {useEffect, useState} from "react";
import Section from "Frontend/components/general/Section";
import {PluginConfigEndpoint} from "Frontend/generated/endpoints";
import {Form, Formik} from "formik";
import {Check} from "@phosphor-icons/react";
import {Button} from "@nextui-org/react";
import Input from "Frontend/components/general/Input";

export default function PluginManagement() {
    const [configSaved, setConfigSaved] = useState(false);
    const [igdbConfigMeta, setIgdbConfigMeta] = useState<any>();
    const [igdbConfig, setIgdbConfig] = useState<any>();

    useEffect(() => {
        PluginConfigEndpoint.getConfigMetadata("igdb").then(setIgdbConfigMeta);
        PluginConfigEndpoint.getConfig("igdb").then(setIgdbConfig);
    }, []);

    useEffect(() => {
        if (configSaved) {
            setTimeout(() => setConfigSaved(false), 2000);
        }
    }, [configSaved])

    async function handleSubmit(values: any) {
        await PluginConfigEndpoint.setConfigEntries("igdb", values);
        setConfigSaved(true);
    }

    return (
        <>
            <Formik
                initialValues={{
                    clientId: igdbConfig?.clientId,
                    clientSecret: igdbConfig?.clientSecret
                }}
                onSubmit={handleSubmit}
            >
                {(formik: { values: any; isSubmitting: any; }) => (
                    <Form>
                        <div className="flex flex-row flex-grow justify-between mb-8">
                            <h2 className="text-2xl font-bold">Plugins</h2>
                            <div className="flex flex-row items-center gap-4">
                                <Button
                                    color="primary"
                                    isLoading={formik.isSubmitting}
                                    disabled={formik.isSubmitting || configSaved}
                                    type="submit"
                                >
                                    {formik.isSubmitting ? "" : configSaved ? <Check/> : "Save"}
                                </Button>
                            </div>
                        </div>

                        <div className="flex flex-row flex-1 justify-between gap-16">
                            <div className="flex flex-col flex-grow">
                                <Section title="IGDB"/>
                                {igdbConfigMeta && igdbConfigMeta.map((entry: any) => (
                                    <Input key={entry.key} name={entry.key} label={entry.name} type="text"/>
                                ))}
                            </div>
                        </div>
                    </Form>
                )}
            </Formik>
        </>
    );
}