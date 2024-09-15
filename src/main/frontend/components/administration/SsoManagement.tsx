import React from "react";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import * as Yup from 'yup';

function SsoMangementLayout({getConfig, formik}: any) {
    return (
        <div className="flex flex-col">

        </div>
    );
}

const validationSchema = Yup.object({});

export const SsoManagement = withConfigPage(SsoMangementLayout, "Single Sign-On", "sso", validationSchema);