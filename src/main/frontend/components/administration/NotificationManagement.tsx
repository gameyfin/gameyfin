import React, {useState} from "react";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import * as Yup from 'yup';
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import Section from "Frontend/components/general/Section";
import {
    Button,
    Card,
    Modal,
    ModalBody,
    ModalContent,
    ModalFooter,
    ModalHeader,
    Textarea,
    useDisclosure
} from "@nextui-org/react";
import {ConfigEndpoint, NotificationEndpoint} from "Frontend/generated/endpoints";
import {toast} from "sonner";
import ConfigEntryDto from "Frontend/generated/de/grimsi/gameyfin/config/dto/ConfigEntryDto";
import {Pencil} from "@phosphor-icons/react";

function NotificationManagementLayout({getConfig, getConfigs, formik}: any) {

    const {isOpen, onOpen, onOpenChange} = useDisclosure();
    const [selectedTemplate, setSelectedTemplate] = useState<ConfigEntryDto | null>(null);

    async function verifyCredentials(provider: string) {
        const credentials: Record<string, any> = {
            host: formik.values.notifications.providers.email.host,
            port: formik.values.notifications.providers.email.port,
            username: formik.values.notifications.providers.email.username,
            password: formik.values.notifications.providers.email.password
        }

        const areCredentialsValid = await NotificationEndpoint.verifyCredentials(provider, credentials);

        if (areCredentialsValid) {
            toast.success("Credentials are valid")
        } else {
            toast.error("Credentials are invalid")
        }
    }

    async function openModal(template: ConfigEntryDto) {
        let templateContent = await ConfigEndpoint.get(template.key);
        setSelectedTemplate({
            ...template,
            value: templateContent
        });
        onOpen();
    }

    async function saveTemplate(template: ConfigEntryDto) {
        await ConfigEndpoint.set(template.key, template.value);
    }

    return (
        <div className="flex flex-col">
            <div className="flex flex-row">
                <div className="flex flex-col flex-1">
                    <ConfigFormField configElement={getConfig("notifications.enabled")}/>

                    <div className="flex flex-row gap-8">
                        <div className="flex flex-col flex-1">
                            <Section title="E-Mail"/>
                            <ConfigFormField configElement={getConfig("notifications.providers.email.host")}
                                             isDisabled={!formik.values.notifications.enabled}/>
                            <ConfigFormField configElement={getConfig("notifications.providers.email.port")}
                                             isDisabled={!formik.values.notifications.enabled}/>
                            <ConfigFormField configElement={getConfig("notifications.providers.email.username")}
                                             isDisabled={!formik.values.notifications.enabled}/>
                            <ConfigFormField configElement={getConfig("notifications.providers.email.password")}
                                             type="password"
                                             isDisabled={!formik.values.notifications.enabled}/>
                            <Button onPress={() => verifyCredentials("email")}
                                    isDisabled={!(
                                        formik.values.notifications.enabled &&
                                        formik.values.notifications.providers.email.host &&
                                        formik.values.notifications.providers.email.port &&
                                        formik.values.notifications.providers.email.username)}>Test</Button>
                        </div>
                        <div className="flex flex-col flex-1">
                            <Section title="Message Templates"/>
                            <div className="flex flex-col gap-4">
                                {getConfigs("notifications.templates").map((template: ConfigEntryDto) =>
                                    <Card className="flex flex-row items-center gap-2 p-4">
                                        <Button isIconOnly
                                                size="sm"
                                                onPress={() => openModal(template)}
                                        >
                                            <Pencil/>
                                        </Button>
                                        <p className="text-lg">{template.description}</p>
                                    </Card>
                                )}
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <Modal isOpen={isOpen} onOpenChange={onOpenChange} size="5xl">
                <ModalContent>
                    {(onClose) => (
                        <>
                            <ModalHeader
                                className="flex flex-col gap-1">Edit {selectedTemplate?.description.toLowerCase()}</ModalHeader>
                            <ModalBody>
                                <Textarea
                                    size="lg"
                                    autoFocus
                                    disableAutosize
                                    value={selectedTemplate?.value}
                                    onChange={(e) => {
                                        if (selectedTemplate?.key) setSelectedTemplate({
                                            ...selectedTemplate,
                                            value: e.target.value
                                        })
                                    }}
                                    classNames={{
                                        input: "resize-y min-h-[500px]"
                                    }}
                                />
                            </ModalBody>
                            <ModalFooter>
                                <Button color="danger" variant="light" onPress={onClose}>
                                    Close
                                </Button>
                                <Button color="primary" onPress={async () => {
                                    if (selectedTemplate) {
                                        await saveTemplate(selectedTemplate);
                                        toast.success("Template saved")
                                        onClose();
                                    }
                                }}>
                                    Save
                                </Button>
                            </ModalFooter>
                        </>
                    )}
                </ModalContent>
            </Modal>
        </div>
    );
}

const validationSchema = Yup.object({});

export const NotificationManagement = withConfigPage(NotificationManagementLayout, "Notifications", "notifications", validationSchema);