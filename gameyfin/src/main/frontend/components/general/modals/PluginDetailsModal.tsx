import React, {useState} from "react";
import {addToast, Button, Link, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader, Tooltip} from "@heroui/react";
import {Form, Formik} from "formik";
import PluginConfigElement from "Frontend/generated/de/grimsi/gameyfin/pluginapi/core/PluginConfigElement";
import Input from "Frontend/components/general/input/Input";
import PluginLogo from "Frontend/components/general/PluginLogo";
import Markdown from "react-markdown";
import remarkBreaks from "remark-breaks";
import {PluginEndpoint} from "Frontend/generated/endpoints";
import PluginDto from "Frontend/generated/de/grimsi/gameyfin/core/plugins/dto/PluginDto";
import {ArrowClockwise} from "@phosphor-icons/react";

interface PluginDetailsModalProps {
    plugin: PluginDto;
    isOpen: boolean;
    onOpenChange: () => void;
}

export default function PluginDetailsModal({plugin, isOpen, onOpenChange}: PluginDetailsModalProps) {
    const [configValidated, setConfigValidated] = useState<boolean>(false);

    async function saveConfig(values: Record<string, string>) {
        await PluginEndpoint.updateConfig(plugin.id, values);
        addToast({
            title: "Configuration saved",
            description: `Configuration for plugin ${plugin.name} saved!`,
            color: "success"
        });
    }

    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="opaque" size="lg">
            <ModalContent>
                {(onClose) => (
                    <Formik initialValues={plugin.config}
                            initialErrors={plugin.configValidation?.errors}
                            enableReinitialize={true}
                            onSubmit={async (values: any) => {
                                await saveConfig(values);
                                onClose();
                            }}
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
                                                            <td className="text-foreground/60 w-0 min-w-20">{key}</td>
                                                            <td className="flex flex-row gap-1">{value}</td>
                                                        </tr>
                                                    )
                                                })}
                                                </tbody>
                                            </table>
                                        </div>
                                        <p className="text-foreground/60">Description</p>
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
                                        <div className="flex-1"/>
                                        {(plugin.configMetadata && plugin.configMetadata.length > 0) && <>
                                            {configValidated &&
                                                <p className="text-small text-success">Validation successful</p>}
                                            <Tooltip content="Re-validate configuration" placement="bottom"
                                                     color="foreground">
                                                <Button isIconOnly variant="light" size="sm"
                                                        onPress={async () => {
                                                            setConfigValidated(false);
                                                            let result = await PluginEndpoint.validateNewConfig(plugin.id, formik.values)
                                                            if (result.errors) formik.setErrors(result.errors);
                                                            else {
                                                                setConfigValidated(true);
                                                                setTimeout(() => setConfigValidated(false), 5000);
                                                            }
                                                        }}>
                                                    <ArrowClockwise/>
                                                </Button>
                                            </Tooltip>
                                        </>}
                                    </div>
                                    {(plugin.configMetadata && plugin.configMetadata.length > 0) ?
                                        plugin.configMetadata.map((entry: PluginConfigElement) => (
                                            <Input key={entry.key} name={entry.key} label={entry.name}
                                                   showErrorUntouched={true}
                                                   type={entry.secret ? "password" : "text"}/>
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