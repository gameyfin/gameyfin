import React, {ReactNode, useState} from "react";
import {Form, Formik, FormikBag, FormikHelpers} from "formik";
import {ArrowLeft, ArrowRight, Check} from "@phosphor-icons/react";
import {Button} from "@heroui/react";
import {Step, Stepper} from "@material-tailwind/react";

const Wizard = ({children, initialValues, onSubmit}: {
    children: ReactNode,
    initialValues: any,
    onSubmit: (values: any, bag: FormikHelpers<any> | FormikBag<any, any>) => Promise<any>
}) => {
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
            /*// @ts-ignore*/
            validationSchema={step.props.validationSchema}
        >
            {(formik) => (
                <Form className="flex flex-col h-full">
                    <div className="w-full mb-8">
                        <Stepper activeStep={stepNumber} activeLineClassName="bg-primary"
                                 lineClassName="bg-foreground"
                                 placeholder={undefined}
                                 onPointerEnterCapture={undefined}
                                 onPointerLeaveCapture={undefined}
                                 onResize={undefined}
                                 onResizeCapture={undefined}>
                            {steps.map((child, index) => (
                                <Step key={index}
                                      className="bg-foreground text-background"
                                      activeClassName="bg-primary"
                                      completedClassName="bg-primary"
                                      placeholder={undefined}
                                      onPointerEnterCapture={undefined}
                                      onPointerLeaveCapture={undefined}
                                      onResize={undefined}
                                      onResizeCapture={undefined}>
                                    {/*@ts-ignore*/}
                                    {child.props.icon}
                                </Step>
                            ))}
                        </Stepper>
                    </div>
                    <div className="flex grow">
                        {step}
                    </div>
                    <div className="left-8 right-8 absolute bottom-8 -z-1">
                        <div className="flex justify-between">
                            <Button color="primary" onClick={() => previous(formik.values)} isDisabled={isFirstStep}>
                                <ArrowLeft/>
                            </Button>
                            <Button
                                color="primary"
                                isLoading={formik.isSubmitting}
                                type="submit"
                            >
                                {formik.isSubmitting ? "" : isLastStep ? <Check/> : <ArrowRight/>}
                            </Button>
                        </div>
                    </div>
                </Form>
            )}
        </Formik>
    );
};

export default Wizard;