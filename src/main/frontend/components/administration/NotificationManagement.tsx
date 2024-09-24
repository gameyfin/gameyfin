import React, {useEffect, useState} from "react";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import Section from "Frontend/components/general/Section";
import {Button, Card, Tooltip, useDisclosure} from "@nextui-org/react";
import {MessageTemplateEndpoint, NotificationEndpoint} from "Frontend/generated/endpoints";
import {toast} from "sonner";
import {PaperPlaneRight, Pencil} from "@phosphor-icons/react";
import MessageTemplateDto from "Frontend/generated/de/grimsi/gameyfin/notifications/templates/MessageTemplateDto";
import SendTestNotificationModal from "Frontend/components/administration/notifications/SendTestNotificationModal";
import EditTemplateModal from "Frontend/components/administration/notifications/EditTemplateModel";

function NotificationManagementLayout({getConfig, getConfigs, formik}: any) {

    const editorModal = useDisclosure();
    const testNotificationModal = useDisclosure();
    const [availableTemplates, setAvailableTemplates] = useState<MessageTemplateDto[]>([]);
    const [selectedTemplate, setSelectedTemplate] = useState<MessageTemplateDto | null>(null);

    useEffect(() => {
        MessageTemplateEndpoint.getAll().then((response: any) => {
            setAvailableTemplates(response as MessageTemplateDto[]);
        });
    }, []);

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

    async function openEditor(template: MessageTemplateDto) {
        setSelectedTemplate(template);
        editorModal.onOpen();
    }

    function openTestNotification(template: MessageTemplateDto) {
        setSelectedTemplate(template);
        testNotificationModal.onOpen();
    }

    return (
        <div className="flex flex-col">
            <div className="flex flex-row">
                <div className="flex flex-col flex-1">
                    <div className="flex flex-row gap-8">
                        <div className="flex flex-col flex-1">
                            <Section title="E-Mail"/>
                            <ConfigFormField configElement={getConfig("notifications.providers.email.enabled")}/>
                            <ConfigFormField configElement={getConfig("notifications.providers.email.host")}
                                             isDisabled={!formik.values.notifications.providers.email.enabled}/>
                            <ConfigFormField configElement={getConfig("notifications.providers.email.port")}
                                             isDisabled={!formik.values.notifications.providers.email.enabled}/>
                            <ConfigFormField configElement={getConfig("notifications.providers.email.username")}
                                             isDisabled={!formik.values.notifications.providers.email.enabled}/>
                            <ConfigFormField configElement={getConfig("notifications.providers.email.password")}
                                             type="password"
                                             isDisabled={!formik.values.notifications.providers.email.enabled}/>
                            <Button onPress={() => verifyCredentials("email")}
                                    isDisabled={!(
                                        formik.values.notifications.providers.email.enabled &&
                                        formik.values.notifications.providers.email.host &&
                                        formik.values.notifications.providers.email.port &&
                                        formik.values.notifications.providers.email.username)}>Test</Button>
                        </div>
                        <div className="flex flex-col flex-1">
                            <Section title="Message Templates"/>
                            <div className="flex flex-col gap-4">
                                {availableTemplates.map((template: MessageTemplateDto) =>
                                    <Card className="flex flex-row items-center gap-2 p-4" key={template.key}>
                                        <Tooltip content="Edit template">
                                            <Button isIconOnly
                                                    size="sm"
                                                    onPress={() => openEditor(template)}
                                            >
                                                <Pencil/>
                                            </Button>
                                        </Tooltip>
                                        <Tooltip content="Send test notification">
                                            <Button isIconOnly
                                                    size="sm"
                                                    onPress={() => openTestNotification(template)}
                                            >
                                                <PaperPlaneRight/>
                                            </Button>
                                        </Tooltip>
                                        <p className="text-lg">{template.description}</p>
                                    </Card>
                                )}
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <EditTemplateModal
                isOpen={editorModal.isOpen}
                onOpenChange={editorModal.onOpenChange}
                selectedTemplate={selectedTemplate}
            />

            <SendTestNotificationModal
                isOpen={testNotificationModal.isOpen}
                onOpenChange={testNotificationModal.onOpenChange}
                selectedTemplate={selectedTemplate}
            />
        </div>
    );
}

export const NotificationManagement = withConfigPage(NotificationManagementLayout, "Notifications", "notifications");