import React, {useState} from "react";
import {Form, Formik, FormikBag, FormikHelpers} from "formik";
import {Button, Spinner, Step, Stepper} from "@material-tailwind/react";
import {ArrowLeft, ArrowRight, Check} from "@phosphor-icons/react";

const Wizard = ({children, initialValues, onSubmit}: { children: any, initialValues: any, onSubmit: any }) => {
    const [stepNumber, setStepNumber] = useState(0);
    const steps = React.Children.toArray(children);
    const [snapshot, setSnapshot] = useState(initialValues);

    const step = steps[stepNumber];
    const totalSteps = steps.length;
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

    const handleSubmit = async (values: any, bag: FormikBag<any, any> | FormikHelpers<any>) => {
        /*// @ts-ignore*/
        if (step.props.onSubmit) {
            /*// @ts-ignore*/
            await step.props.onSubmit(values, bag);
        }
        if (isLastStep) {
            return onSubmit(values, bag);
        } else {
            await bag.setTouched({});
            next(values);
        }
    };

    return (
        <Formik
            initialValues={snapshot}
            onSubmit={handleSubmit}
            isInitialValid={false}
            /*// @ts-ignore*/
            validationSchema={step.props.validationSchema}
        >
            {formik => (
                <Form className="flex flex-col grow">
                    <div className="w-full mb-8">
                        <Stepper
                            activeStep={stepNumber}
                        >
                            {steps.map((child, index) => (
                                <Step key={index}>
                                    {/*// @ts-ignore*/}
                                    {child.props.icon}
                                </Step>
                            ))}
                        </Stepper>
                    </div>
                    <div className="flex grow">
                        {step}
                    </div>
                    <div className="bottom-0 w-full">
                        <div className="flex justify-between">
                            <Button onClick={() => previous(formik.values)} disabled={isFirstStep}
                                    className="rounded-full">
                                <ArrowLeft/>
                            </Button>
                            <Button disabled={formik.isSubmitting}
                                    className="rounded-full"
                                    type="submit"
                            >
                                {formik.isSubmitting ?
                                    <Spinner className="h-5 w-5"/> : isLastStep ? <Check/> : <ArrowRight/>
                                }
                            </Button>
                        </div>
                    </div>
                </Form>
            )}
        </Formik>
    );
};

export default Wizard;