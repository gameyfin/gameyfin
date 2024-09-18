import React from "react";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import * as Yup from 'yup';
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import Section from "Frontend/components/general/Section";
import {Button, Input, Select, SelectItem} from "@nextui-org/react";
import {NotificationEndpoint} from "Frontend/generated/endpoints";
import EmailCredentialsDto from "Frontend/generated/de/grimsi/gameyfin/notifications/dto/EmailCredentialsDto";
import {toast} from "sonner";

function NotificationManagementLayout({getConfig, formik}: any) {

    async function testMailSettings() {
        const credentials: EmailCredentialsDto = {
            host: formik.values.notifications.email.host,
            port: formik.values.notifications.email.port,
            username: formik.values.notifications.email.username,
            password: formik.values.notifications.email.password
        }

        const areCredentialsValid = await NotificationEndpoint.verifyEmailCredentials(credentials);

        if (areCredentialsValid) {
            toast.success("Credentials are valid")
        } else {
            toast.error("Credentials are invalid")
        }
    }

    return (
        <div className="flex flex-col">
            <div className="flex flex-row">
                <div className="flex flex-col flex-1">
                    <ConfigFormField configElement={getConfig("notifications.enabled")}/>

                    <div className="flex flex-row gap-8">
                        <div className="flex flex-col flex-1">
                            <Section title="E-Mail"/>
                            <ConfigFormField configElement={getConfig("notifications.email.host")}
                                             isDisabled={!formik.values.notifications.enabled}/>
                            <ConfigFormField configElement={getConfig("notifications.email.port")}
                                             isDisabled={!formik.values.notifications.enabled}/>
                            <ConfigFormField configElement={getConfig("notifications.email.username")}
                                             isDisabled={!formik.values.notifications.enabled}/>
                            <ConfigFormField configElement={getConfig("notifications.email.password")}
                                             type="password"
                                             isDisabled={!formik.values.notifications.enabled}/>
                            <Button onPress={testMailSettings}
                                    isDisabled={!(
                                        formik.values.notifications.enabled &&
                                        formik.values.notifications.email.host &&
                                        formik.values.notifications.email.port &&
                                        formik.values.notifications.email.username)}>Test</Button>
                        </div>
                        <div className="flex flex-col flex-1">
                            <Section title="Push"/>
                            {/* TODO: Evaluate need and options if need is given */}
                            <Select className="mt-2 mb-10"
                                    label="Push notification provider" defaultSelectedKeys={["pushbullet"]}
                                    isDisabled>
                                <SelectItem key="pushbullet">Pushbullet</SelectItem>
                            </Select>
                            <Input className="mt-2 mb-10" label="Access Token" type="password" isDisabled/>
                            <Input className="mt-2 mb-10" label="Channel tag" type="text" isDisabled/>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

const validationSchema = Yup.object({});

export const NotificationManagement = withConfigPage(NotificationManagementLayout, "Notifications", "notifications", validationSchema);