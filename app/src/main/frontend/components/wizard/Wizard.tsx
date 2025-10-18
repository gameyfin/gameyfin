import React, {ReactNode, useState} from "react";
import {Form, Formik, FormikBag, FormikHelpers} from "formik";
import {ArrowLeftIcon, ArrowRightIcon, CheckIcon} from "@phosphor-icons/react";
import {Button} from "@heroui/react";
import Stepper from "./Stepper";

type WizardProps = {
    children: ReactNode;
    initialValues: any;
    onSubmit: (values: any, bag: FormikHelpers<any> | FormikBag<any, any>) => Promise<any>;
};

const Wizard = ({children, initialValues, onSubmit}: WizardProps) => {
    const allSteps = React.Children.toArray(children);
    const [stepNumber, setStepNumber] = useState(0);
    const [snapshot, setSnapshot] = useState(initialValues);

    const step = allSteps[stepNumber];
    const totalSteps = allSteps.length;
    const isFirstStep = stepNumber === 0;
    const isLastStep = stepNumber === totalSteps - 1;

    const next = (values: any) => {
        setSnapshot(values);
        setStepNumber(Math.min(stepNumber + 1, totalSteps - 1));
    };

    const previous = (values: any) => {
        setSnapshot(values);
        setStepNumber(Math.max(stepNumber - 1, 0));
    };

    const handleSubmit = async (values: any, bag: FormikHelpers<any> | FormikBag<any, any>) => {
        // per-step custom submit if provided
        // @ts-ignore
        if (step.props.onSubmit) {
            // @ts-ignore
            await step.props.onSubmit(values, bag);
        }
        if (isLastStep) {
            return onSubmit(values, bag);
        } else {
            await bag.setTouched({});
            next(values);
        }
    };

    const stepsMeta = allSteps.map((child: any) => ({
        title: child.props?.title ?? child.props?.label,
        icon: child.props?.icon
    }));

    return (
        <Formik
            initialValues={snapshot}
            onSubmit={handleSubmit}
            // @ts-ignore
            validationSchema={step.props?.validationSchema}
            enableReinitialize={false}
        >
            {(formik) => (
                <Form className="flex flex-col h-full">
                    <div className="w-full mb-8">
                        <Stepper
                            steps={stepsMeta}
                            currentStep={stepNumber}
                            onStepChange={(idx) => {
                                // only allow backwards navigation to keep validation flow
                                if (idx <= stepNumber) setStepNumber(idx);
                            }}
                            hideProgressBars={false}
                        />
                    </div>
                    <div className="flex grow">{step}</div>
                    <div className="left-8 right-8 absolute bottom-8">
                        <div className="flex justify-between">
                            <Button
                                color="primary"
                                onPress={() => previous(formik.values)}
                                isDisabled={isFirstStep || formik.isSubmitting}
                            >
                                <ArrowLeftIcon/>
                            </Button>
                            <Button color="primary" isLoading={formik.isSubmitting} type="submit">
                                {formik.isSubmitting ? "" : isLastStep ? <CheckIcon/> : <ArrowRightIcon/>}
                            </Button>
                        </div>
                    </div>
                </Form>
            )}
        </Formik>
    );
};

export default Wizard;
