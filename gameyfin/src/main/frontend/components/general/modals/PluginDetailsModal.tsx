import React, {useState} from "react";
import {addToast, Button, Link, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader, Tooltip} from "@heroui/react";
import {Form, Formik} from "formik";
import PluginLogo from "Frontend/components/general/plugin/PluginLogo";
import Markdown from "react-markdown";
import remarkBreaks from "remark-breaks";
import {PluginEndpoint} from "Frontend/generated/endpoints";
import PluginDto from "Frontend/generated/de/grimsi/gameyfin/core/plugins/dto/PluginDto";
import {ArrowClockwise} from "@phosphor-icons/react";
import PluginConfigMetadataDto from "Frontend/generated/de/grimsi/gameyfin/core/plugins/dto/PluginConfigMetadataDto";
import PluginConfigFormField from "Frontend/components/general/plugin/PluginConfigFormField";

interface PluginDetailsModalProps {
    plugin: PluginDto;
    isOpen: boolean;
    onOpenChange: () => void;
}

enum ValidationState {
    UNCHECKED,
    VALID,
    INVALID,
    IN_PROGRESS
}

export default function PluginDetailsModal({plugin, isOpen, onOpenChange}: PluginDetailsModalProps) {
    const [configValidated, setConfigValidated] = useState<ValidationState>(ValidationState.UNCHECKED);

    async function saveConfig(values: Record<string, string>) {
        await PluginEndpoint.updateConfig(plugin.id, values);
        addToast({
            title: "Configuration saved",
            description: `Configuration for plugin ${plugin.name} saved!`,
            color: "success"
        });
    }

    function getEffectiveConfig(): Record<string, any> {
        const effectiveConfig: Record<string, any> = {};
        if (!plugin.configMetadata) return effectiveConfig;

        for (const meta of plugin.configMetadata) {
            const key = meta.key;
            let value = plugin.config?.[key] ?? meta.default;

            if (value != null) {
                switch (meta.type.toLowerCase()) {
                    case "float":
                    case "int":
                        effectiveConfig[key] = Number(value);
                        break;
                    case "boolean":
                        effectiveConfig[key] = value === true || value === "true";
                        break;
                    default:
                        effectiveConfig[key] = value.toString();
                }
            }
        }
        return effectiveConfig;
    }

    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="opaque" size="lg">
            <ModalContent>
                {(onClose) => {

                    async function handleSubmit(values: Record<string, string>): Promise<void> {
                        await saveConfig(values);
                        onClose();
                    }

                    return (
                        <Formik initialValues={getEffectiveConfig()}
                                initialErrors={plugin.configValidation?.errors}
                                enableReinitialize={true}
                                onSubmit={handleSubmit}
                        >
                            {(formik: any) => (
                                <Form>
                                    <ModalHeader className="flex flex-col gap-1">
                                        Plugin configuration for {plugin.name}
                                    </ModalHeader>
                                    <ModalBody>
                                        <div className="flex flex-col text-sm">
                                            <div className="flex flex-row items-center gap-8 mb-4">
                                                <PluginLogo plugin={plugin}/>
                                                <table className="text-left table-auto">
                                                    <tbody>
                                                    {Object.entries({
                                                        "Author": plugin.author,
                                                        "Version": plugin.version,
                                                        "License": plugin.license,
                                                        "URL": <Link isExternal
                                                                     showAnchorIcon
                                                                     color="foreground"
                                                                     size="sm"
                                                                     href={plugin.url}>
                                                            {plugin.url}
                                                        </Link>,
                                                    }).map(([key, value]) => {
                                                        if (!value) return;
                                                        return (
                                                            <tr key={key}>
                                                                <td className="text-default-500 w-0 min-w-20">{key}</td>
                                                                <td className="flex flex-row gap-1">{value}</td>
                                                            </tr>
                                                        )
                                                    })}
                                                    </tbody>
                                                </table>
                                            </div>
                                            <p className="text-default-500">Description</p>
                                            <Markdown
                                                remarkPlugins={[remarkBreaks]}
                                                components={{
                                                    a(props) {
                                                        return <Link isExternal
                                                                     showAnchorIcon
                                                                     color="foreground"
                                                                     underline="always"
                                                                     href={props.href}
                                                                     size="sm">
                                                            {props.children}
                                                        </Link>
                                                    }
                                                }}
                                            >{plugin.description}</Markdown>
                                        </div>

                                        <div className="flex flex-row items-center mt-4 gap-2">
                                            <h4 className="text-l font-bold">Configuration</h4>
                                            {(plugin.configMetadata && plugin.configMetadata.length > 0) && <>
                                                <div className="flex-1"/>
                                                {(() => {
                                                    switch (configValidated) {
                                                        case ValidationState.VALID:
                                                            return <p className="text-small text-success">
                                                                Configuration valid
                                                            </p>;
                                                        case ValidationState.INVALID:
                                                            return <p className="text-small text-danger">
                                                                Configuration invalid
                                                            </p>;
                                                        default:
                                                            return null;
                                                    }
                                                })()}
                                                <Tooltip content="Re-validate configuration" placement="bottom"
                                                         color="foreground">
                                                    <Button isIconOnly variant="light" size="sm"
                                                            isLoading={configValidated === ValidationState.IN_PROGRESS}
                                                            onPress={async () => {
                                                                setConfigValidated(ValidationState.IN_PROGRESS);
                                                                let result = await PluginEndpoint.validateNewConfig(plugin.id, formik.values)
                                                                if (result.errors) {
                                                                    formik.setErrors(result.errors);
                                                                    setConfigValidated(ValidationState.INVALID);
                                                                } else {
                                                                    setConfigValidated(ValidationState.VALID);
                                                                }
                                                                setTimeout(() => setConfigValidated(ValidationState.UNCHECKED), 5000);
                                                            }}>
                                                        <ArrowClockwise/>
                                                    </Button>
                                                </Tooltip>
                                            </>}
                                        </div>
                                        {(plugin.configMetadata && plugin.configMetadata.length > 0) ?
                                            plugin.configMetadata.map((entry: PluginConfigMetadataDto) => (
                                                <PluginConfigFormField
                                                    key={entry.key}
                                                    pluginConfigMetadata={entry}
                                                    showErrorUntouched={true}/>
                                            )) : "This plugin has no configuration options."
                                        }
                                    </ModalBody>
                                    <ModalFooter>
                                        <Button variant="light" onPress={onClose}>
                                            Cancel
                                        </Button>
                                        {(plugin.configMetadata && plugin.configMetadata?.length > 0) ?
                                            <Button
                                                color="primary"
                                                isLoading={formik.isSubmitting}
                                                isDisabled={formik.isSubmitting || !formik.dirty}
                                                type="submit"
                                            >
                                                {formik.isSubmitting ? "" : "Save"}
                                            </Button> : ""}
                                    </ModalFooter>
                                </Form>
                            )
                            }
                        </Formik>
                    )
                }}
            </ModalContent>
        </Modal>
    );
}