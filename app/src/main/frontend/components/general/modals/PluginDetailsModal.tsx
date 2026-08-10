import React, {useState} from "react";
import {Button, Link, Modal, toast, Tooltip} from "@heroui/react";
import {Form, Formik} from "formik";
import PluginLogo from "Frontend/components/general/plugin/PluginLogo";
import Markdown from "react-markdown";
import remarkBreaks from "remark-breaks";
import {PluginEndpoint} from "Frontend/generated/endpoints";
import PluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/PluginDto";
import {ArrowClockwiseIcon} from "@phosphor-icons/react";
import PluginConfigMetadataDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/PluginConfigMetadataDto";
import PluginConfigFormField from "Frontend/components/general/plugin/PluginConfigFormField";

interface PluginDetailsModalProps {
    plugin: PluginDto;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
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
        toast.success("Configuration saved", {
            description: `Configuration for plugin ${plugin.name} saved!`
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
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
                <Modal.Container size="lg">
                    <Modal.Dialog>
                        {({close}) => {

                            async function handleSubmit(values: Record<string, string>): Promise<void> {
                                await saveConfig(values);
                                close();
                            }

                            return (
                                <Formik initialValues={getEffectiveConfig()}
                                        initialErrors={plugin.configValidation?.errors}
                                        enableReinitialize={true}
                                        onSubmit={handleSubmit}
                                >
                                    {(formik: any) => (
                                        <Form>
                                            <Modal.Header>
                                                <Modal.Heading>Plugin configuration for {plugin.name}</Modal.Heading>
                                            </Modal.Header>
                                            <Modal.Body>
                                                <div className="flex flex-col text-sm">
                                                    <div className="flex flex-row items-center gap-8 mb-4">
                                                        <PluginLogo plugin={plugin}/>
                                                        <table className="text-left table-auto">
                                                            <tbody>
                                                            {Object.entries({
                                                                "Author(s)": plugin.author,
                                                                "Version": plugin.version,
                                                                "License": plugin.license,
                                                                "URL": <Link target="_blank" rel="noopener noreferrer"
                                                                             className="text-sm underline"
                                                                             href={plugin.url}>
                                                                    {plugin.url}
                                                                </Link>,
                                                            }).map(([key, value]) => {
                                                                if (!value) return;
                                                                return (
                                                                    <tr key={key}>
                                                                        <td className="text-muted w-0 min-w-20">{key}</td>
                                                                        <td className="flex flex-row gap-1">{value}</td>
                                                                    </tr>
                                                                )
                                                            })}
                                                            </tbody>
                                                        </table>
                                                    </div>
                                                    <p className="text-muted">Description</p>
                                                    <Markdown
                                                        remarkPlugins={[remarkBreaks]}
                                                        components={{
                                                            a(props) {
                                                                return <Link target="_blank" rel="noopener noreferrer"
                                                                             className="underline text-sm"
                                                                             href={props.href}>
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
                                                        <Tooltip>
                                                            <Tooltip.Trigger>
                                                                <Button isIconOnly variant="tertiary" size="sm"
                                                                        isPending={configValidated === ValidationState.IN_PROGRESS}
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
                                                                    <ArrowClockwiseIcon/>
                                                                </Button>
                                                            </Tooltip.Trigger>
                                                            <Tooltip.Content placement="bottom">Re-validate
                                                                configuration</Tooltip.Content>
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
                                            </Modal.Body>
                                            <Modal.Footer>
                                                <Button variant="tertiary" onPress={close}>
                                                    Cancel
                                                </Button>
                                                {(plugin.configMetadata && plugin.configMetadata?.length > 0) ?
                                                    <Button
                                                        variant="primary"
                                                        isPending={formik.isSubmitting}
                                                        isDisabled={formik.isSubmitting || !formik.dirty}
                                                        type="submit"
                                                    >
                                                        {formik.isSubmitting ? "" : "Save"}
                                                    </Button> : ""}
                                            </Modal.Footer>
                                        </Form>
                                    )
                                    }
                                </Formik>
                            )
                        }}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}