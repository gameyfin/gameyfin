import React from "react";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import * as Yup from 'yup';
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import Section from "Frontend/components/general/Section";
import {Button} from "@nextui-org/react";
import {MagicWand} from "@phosphor-icons/react";
import {toast} from "sonner";

function SsoMangementLayout({getConfig, formik}: any) {

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
            toast.error("Failed to auto-populate SSO configuration");
        }
    }

    return (
        <div className="flex flex-col">
            <div className="flex flex-row">
                <div className="flex flex-col flex-1">
                    <ConfigFormField configElement={getConfig("sso.oidc.enabled")}/>

                    <Section title="SSO user handling"/>
                    <div className="flex flex-row">
                        <ConfigFormField configElement={getConfig("sso.oidc.auto-register-new-users")}
                                         isDisabled={!formik.values.sso.oidc.enabled}/>
                        <ConfigFormField configElement={getConfig("sso.oidc.match-existing-users-by")}
                                         isDisabled={!formik.values.sso.oidc.enabled}/>
                    </div>

                    <Section title="SSO provider configuration"/>
                    <ConfigFormField configElement={getConfig("sso.oidc.client-id")}
                                     isDisabled={!formik.values.sso.oidc.enabled}/>
                    <ConfigFormField configElement={getConfig("sso.oidc.client-secret")}
                                     isDisabled={!formik.values.sso.oidc.enabled}/>
                    <div className="flex flex-row gap-2">
                        <ConfigFormField configElement={getConfig("sso.oidc.issuer-url")}
                                         isDisabled={!formik.values.sso.oidc.enabled}/>
                        <Button
                            isDisabled={isAutoPopulateDisabled()}
                            onPress={autoPopulate}
                            className="h-14 mt-2"><MagicWand/> Auto-populate</Button>
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

const validationSchema = Yup.object({});

export const SsoManagement = withConfigPage(SsoMangementLayout, "Single Sign-On", "sso", validationSchema);