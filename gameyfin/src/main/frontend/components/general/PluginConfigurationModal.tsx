import React, {useEffect, useState} from "react";
import {Button, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader} from "@nextui-org/react";
import {toast} from "sonner";
import {Form, Formik} from "formik";
import {PluginConfigEndpoint} from "Frontend/generated/endpoints";
import PluginDto from "Frontend/generated/de/grimsi/gameyfin/core/plugins/management/PluginDto";
import PluginConfigElement from "Frontend/generated/de/grimsi/gameyfin/pluginapi/core/PluginConfigElement";
import Input from "Frontend/components/general/Input";

interface PluginConfigurationModalProps {
    plugin: PluginDto;
    isOpen: boolean;
    onOpenChange: () => void;
}

export default function PluginConfigurationModal({plugin, isOpen, onOpenChange}: PluginConfigurationModalProps) {
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
                                <ModalHeader className="flex flex-col gap-1">{plugin.name} configuration</ModalHeader>
                                <ModalBody>
                                    {pluginConfigMeta && pluginConfigMeta.map((entry: any) => (
                                        <Input key={entry.key} name={entry.key} label={entry.name} type="text"/>
                                    ))}
                                </ModalBody>
                                <ModalFooter>
                                    <Button variant="light" onPress={onClose}>
                                        Cancel
                                    </Button>
                                    <Button
                                        color="primary"
                                        isLoading={formik.isSubmitting}
                                        disabled={formik.isSubmitting}
                                        type="submit"
                                    >
                                        {formik.isSubmitting ? "" : "Save"}
                                    </Button>
                                </ModalFooter>
                            </Form>
                        )}
                    </Formik>
                )}
            </ModalContent>
        </Modal>
    );
}