import React, {useEffect} from "react";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import * as Yup from 'yup';
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import Section from "Frontend/components/general/Section";
import {addToast, Button} from "@heroui/react";
import {MagicWand} from "@phosphor-icons/react";

function SsoManagementLayout({getConfig, formik, setSaveMessage}: any) {

    useEffect(() => {
        if (formik.dirty) {
            setSaveMessage("Gameyfin must be restarted for the changes to take effect");
        } else {
            setSaveMessage(null);
        }
    }, [formik.dirty]);

    function isAutoPopulateDisabled() {
        return !formik.values.sso.oidc.enabled || !formik.values.sso.oidc["issuer-url"];
    }

    async function autoPopulate() {
        let issuerUrl: string = formik.values.sso.oidc["issuer-url"];
        if (issuerUrl.endsWith("/")) issuerUrl = issuerUrl.slice(0, -1);

        try {
            const response = await fetch(issuerUrl + "/.well-known/openid-configuration");
            const data = await response.json();

            formik.setFieldValue("sso.oidc.authorize-url", data.authorization_endpoint);
            formik.setFieldValue("sso.oidc.token-url", data.token_endpoint);
            formik.setFieldValue("sso.oidc.userinfo-url", data.userinfo_endpoint);
            formik.setFieldValue("sso.oidc.logout-url", data.end_session_endpoint);
            formik.setFieldValue("sso.oidc.jwks-url", data.jwks_uri);
        } catch (e) {
            addToast({
                title: "Failed to auto-populate SSO configuration",
                color: "warning"
            });
        }
    }

    return (
        <div className="flex flex-col">
            <div className="flex flex-row">
                <div className="flex flex-col flex-1">
                    <Section title="SSO configuration"/>
                    <ConfigFormField configElement={getConfig("sso.oidc.enabled")}/>

                    <Section title="SSO user handling"/>
                    <div className="flex flex-row">
                        <ConfigFormField configElement={getConfig("sso.oidc.auto-register-new-users")}
                                         isDisabled={!formik.values.sso.oidc.enabled}/>
                        <div className="flex flex-row flex-1 justify-center gap-2">
                            <ConfigFormField configElement={getConfig("sso.oidc.match-existing-users-by")}
                                             isDisabled={!formik.values.sso.oidc.enabled ||
                                                 !formik.values.sso.oidc["auto-register-new-users"]}/>
                        </div>
                    </div>

                    <Section title="SSO provider configuration"/>
                    <ConfigFormField configElement={getConfig("sso.oidc.client-id")}
                                     isDisabled={!formik.values.sso.oidc.enabled}/>
                    <ConfigFormField configElement={getConfig("sso.oidc.client-secret")}
                                     type="password"
                                     isDisabled={!formik.values.sso.oidc.enabled}/>
                    <div className="flex flex-row gap-2">
                        <ConfigFormField configElement={getConfig("sso.oidc.issuer-url")}
                                         isDisabled={!formik.values.sso.oidc.enabled}/>
                        <Button
                            isDisabled={isAutoPopulateDisabled()}
                            onPress={autoPopulate}
                            className="h-14"><MagicWand className="min-w-5"/> Auto-populate</Button>
                    </div>
                    <ConfigFormField configElement={getConfig("sso.oidc.authorize-url")}
                                     isDisabled={!formik.values.sso.oidc.enabled}/>
                    <ConfigFormField configElement={getConfig("sso.oidc.token-url")}
                                     isDisabled={!formik.values.sso.oidc.enabled}/>
                    <ConfigFormField configElement={getConfig("sso.oidc.userinfo-url")}
                                     isDisabled={!formik.values.sso.oidc.enabled}/>
                    <ConfigFormField configElement={getConfig("sso.oidc.logout-url")}
                                     isDisabled={!formik.values.sso.oidc.enabled}/>
                    <ConfigFormField configElement={getConfig("sso.oidc.jwks-url")}
                                     isDisabled={!formik.values.sso.oidc.enabled}/>
                </div>
            </div>
        </div>
    );
}

const validationSchema = Yup.object({
    sso: Yup.object({
        oidc: Yup.object({
            enabled: Yup.boolean(),
            "auto-register-new-users": Yup.boolean().required(),
            "match-existing-users-by": Yup.string().required(),
            "client-id": Yup.string().when("enabled", ([enabled], schema) =>
                enabled ? schema.required("Client ID is required") : schema
            ),
            "client-secret": Yup.string().when("enabled", ([enabled], schema) =>
                enabled ? schema.required("Client Secret is required") : schema
            ),
            "issuer-url": Yup.string().when("enabled", ([enabled], schema) =>
                enabled ? schema.required("Issuer URL is required") : schema
            ),
            "authorize-url": Yup.string().when("enabled", ([enabled], schema) =>
                enabled ? schema.required("Authorize URL is required") : schema
            ),
            "token-url": Yup.string().when("enabled", ([enabled], schema) =>
                enabled ? schema.required("Token URL is required") : schema
            ),
            "userinfo-url": Yup.string().when("enabled", ([enabled], schema) =>
                enabled ? schema.required("Userinfo URL is required") : schema
            ),
            "logout-url": Yup.string().when("enabled", ([enabled], schema) =>
                enabled ? schema.required("Logout URL is required") : schema
            ),
            "jwks-url": Yup.string().when("enabled", ([enabled], schema) =>
                enabled ? schema.required("JWKS URL is required") : schema
            )
        })
    })
});

export const SsoManagement = withConfigPage(SsoManagementLayout, "Single Sign-On", validationSchema);