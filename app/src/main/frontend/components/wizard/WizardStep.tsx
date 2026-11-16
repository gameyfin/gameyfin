import {ReactNode} from "react";
import * as Yup from 'yup';

export type WizardStepProps = {
    children: ReactNode,
    icon: ReactNode,
    title?: string,
    validationSchema?: Yup.Schema,
    isNextDisabled?: boolean,
    nextLabel?: string,
    onSubmit?: (...values: any) => Promise<void | boolean>
};

export default function WizardStep({children}: WizardStepProps) {
    return children;
}
