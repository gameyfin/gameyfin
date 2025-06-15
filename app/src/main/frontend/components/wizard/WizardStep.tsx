import {JSX, ReactNode} from "react";
import * as Yup from 'yup';

export default function WizardStep({children, icon, validationSchema}: {
    children: ReactNode,
    icon: JSX.Element,
    validationSchema?: Yup.Schema,
    onSubmit?: (...values: any) => Promise<void>
}) {
    return children;
}