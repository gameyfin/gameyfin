import React, {useEffect, useState} from "react";
import {Button, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader} from "@nextui-org/react";
import {toast} from "sonner";
import {Form, Formik} from "formik";
import {PluginConfigEndpoint, PluginManagementEndpoint} from "Frontend/generated/endpoints";
import PluginDto from "Frontend/generated/de/grimsi/gameyfin/core/plugins/management/PluginDto";
import PluginConfigElement from "Frontend/generated/de/grimsi/gameyfin/pluginapi/core/PluginConfigElement";
import Input from "Frontend/components/general/Input";
import {Plug} from "@phosphor-icons/react";

interface PluginDetailsModalProps {
    plugin: PluginDto;
    isOpen: boolean;
    onOpenChange: () => void;
    updatePlugin: (plugin: PluginDto) => void;
}

export default function PluginDetailsModal({plugin, isOpen, onOpenChange, updatePlugin}: PluginDetailsModalProps) {
    const [pluginConfigMeta, setPluginConfigMeta] = useState<(PluginConfigElement)[]>();
    const [pluginConfig, setPluginConfig] = useState<Record<string, string>>();

    useEffect(() => {
        PluginConfigEndpoint.getConfigMetadata(plugin.id).then(response => {
            if (response === undefined) return;
            setPluginConfigMeta(response as PluginConfigElement[]);
        });
        PluginConfigEndpoint.getConfig(plugin.id).then(response => {
            if (response === undefined) return;
            setPluginConfig(response as Record<string, string>);
        });
    }, []);

    async function saveConfig(values: Record<string, string>) {
        await PluginConfigEndpoint.setConfigEntries(plugin.id, values);
        toast.success(`Configuration for ${plugin.name} saved!`);
        let updatedPlugin = await PluginManagementEndpoint.getPlugin(plugin.id);
        if (updatedPlugin === undefined) return;
        updatePlugin(updatedPlugin);
    }

    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="opaque" size="lg">
            <ModalContent>
                {(onClose) => (
                    <Formik initialValues={pluginConfig}
                            enableReinitialize={true}
                            onSubmit={async (values: any) => {
                                await saveConfig(values);
                                onClose();
                            }}
                    >
                        {(formik: { isSubmitting: any; }) => (
                            <Form>
                                <ModalHeader className="flex flex-col gap-1">
                                    Plugin configuration for {plugin.name}</ModalHeader>
                                <ModalBody>
                                    <h4 className="text-l font-bold">Details</h4>
                                    <div className="flex flex-row gap-8">
                                        <Plug size={64} weight="fill"/>
                                        <div className="grid grid-cols-2">
                                            <p>Author: {plugin.author}</p>
                                            <p>Version: {plugin.version}</p>
                                            <p>Plugin ID: {plugin.id}</p>
                                            <p>Status: {plugin.state?.toLowerCase()}</p>
                                        </div>
                                    </div>

                                    <h4 className="text-l font-bold mt-6">Configuration</h4>
                                    {(pluginConfigMeta && pluginConfigMeta.length > 0) ?
                                        pluginConfigMeta.map((entry: any) => (
                                            <Input key={entry.key} name={entry.key} label={entry.name} type="text"/>
                                        )) : "This plugin has no configuration options."
                                    }
                                </ModalBody>
                                <ModalFooter>
                                    <Button variant="light" onPress={onClose}>
                                        Cancel
                                    </Button>
                                    {(pluginConfigMeta && pluginConfigMeta?.length > 0) ?
                                        <Button
                                            color="primary"
                                            isLoading={formik.isSubmitting}
                                            disabled={formik.isSubmitting}
                                            type="submit"
                                        >
                                            {formik.isSubmitting ? "" : "Save"}
                                        </Button> : ""}
                                </ModalFooter>
                            </Form>
                        )}
                    </Formik>
                )}
            </ModalContent>
        </Modal>
    );
}