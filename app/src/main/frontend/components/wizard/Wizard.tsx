import React, {ReactElement, ReactNode, useEffect, useRef, useState} from "react";
import {Form, Formik, FormikBag, FormikHelpers} from "formik";
import {ArrowLeftIcon, ArrowRightIcon, CheckIcon} from "@phosphor-icons/react";
import {Alert, Button, Modal, Spinner} from "@heroui/react";
import Stepper from "./Stepper";
import {WizardStepProps} from "./WizardStep";

type WizardProps = {
    children: ReactNode;
    initialValues: any;
    onSubmit: (values: any, bag: FormikHelpers<any> | FormikBag<any, any>) => Promise<any>;
    variant?: "page" | "modal";
    keepStepsMounted?: boolean;
    isBusy?: boolean;
    finishLabel?: string;
    finishIcon?: ReactNode;
    onCancel?: () => void;
    status?: ReactNode;
};

const Wizard = ({children, initialValues, onSubmit, variant = "page", keepStepsMounted = false,
                    isBusy = false, finishLabel = "Finish setup", finishIcon = <CheckIcon/>, onCancel, status}: WizardProps) => {
    const allSteps = React.Children.toArray(children) as ReactElement<WizardStepProps>[];
    const [stepNumber, setStepNumber] = useState(0);
    const [snapshot, setSnapshot] = useState(initialValues);
    const [error, setError] = useState("");
    const bodyRef = useRef<HTMLDivElement>(null);
    useEffect(() => { bodyRef.current?.scrollTo({top: 0}); }, [stepNumber]);
    const modal = variant === "modal";
    const step = allSteps[stepNumber];
    const totalSteps = allSteps.length;
    const isFirstStep = stepNumber === 0;
    const isLastStep = stepNumber === totalSteps - 1;

    const next = (values: any) => {
        setSnapshot(values);
        setStepNumber(Math.min(stepNumber + 1, totalSteps - 1));
    };

    const previous = (values: any) => {
        setError("");
        setSnapshot(values);
        setStepNumber(Math.max(stepNumber - 1, 0));
    };

    const handleSubmit = async (values: any, bag: FormikHelpers<any> | FormikBag<any, any>) => {
        if (isBusy || step.props.isNextDisabled) return;
        setError("");
        try {
            // per-step custom submit if provided
            if (step.props.onSubmit && await step.props.onSubmit(values, bag) === false) return;
            if (isLastStep) {
                await onSubmit(values, bag);
            } else {
                await bag.setTouched({});
                next(values);
            }
        } catch (error) {
            setError(error instanceof Error ? error.message : "Unable to complete this step. Try again.");
        }
    };

    const stepsMeta = allSteps.map(child => ({title: child.props.title, icon: child.props.icon}));

    return <Formik initialValues={snapshot} onSubmit={handleSubmit}
                   validationSchema={step.props.validationSchema} enableReinitialize={false}>
        {formik => {
            const blocked = isBusy || formik.isSubmitting;
            const navigation = <>
                {onCancel && <Button variant="ghost" isDisabled={blocked} onPress={onCancel}>Cancel</Button>}
                <div className={`flex items-center gap-2 ${modal ? "ml-auto" : "w-full justify-between"}`}>
                    <Button variant={modal ? "secondary" : "primary"} aria-label="Previous step"
                            isIconOnly={!modal} onPress={() => previous(formik.values)}
                            isDisabled={isFirstStep || blocked}>
                        <ArrowLeftIcon/>{modal && "Back"}
                    </Button>
                    <Button aria-label={isLastStep ? finishLabel : step.props.nextLabel ?? "Next step"}
                            variant="primary" isIconOnly={!modal} isPending={formik.isSubmitting}
                            isDisabled={blocked || step.props.isNextDisabled} type="submit">
                        {formik.isSubmitting ? <Spinner size="sm" color="current"/> : isLastStep ? finishIcon : <ArrowRightIcon/>}
                        {modal && (isLastStep ? finishLabel : step.props.nextLabel ?? "Next")}
                    </Button>
                </div>
            </>;
            const content = <div className="min-w-0 w-full" inert={blocked}>
                {keepStepsMounted ? allSteps.map((child, index) =>
                    <div key={index} hidden={index !== stepNumber}>{child}</div>) : step}
            </div>;
            return <Form className={modal ? "flex flex-col flex-1 min-h-0" : "flex flex-col h-full"}>
                <div className={modal ? "px-6 pb-3 shrink-0" : "w-full mb-8"}>
                    <Stepper steps={stepsMeta} currentStep={stepNumber} disabled={blocked} className={modal ? "px-3" : undefined}
                             onStepChange={idx => {
                                 // only allow backwards navigation to keep validation flow
                                 if (!blocked && idx <= stepNumber) {
                                     setSnapshot(formik.values);
                                     setError("");
                                     setStepNumber(idx);
                                 }
                             }} hideProgressBars={false}/>
                </div>
                <p className="sr-only" aria-live="polite">Step {stepNumber + 1} of {totalSteps}{step.props.title && `: ${step.props.title}`}</p>
                {modal ? <Modal.Body ref={bodyRef}>{content}</Modal.Body> : <div className="flex grow">{content}</div>}
                {error && <div className={modal ? "px-6 pt-3" : "mt-4"}>
                    <Alert status="danger"><Alert.Indicator/><Alert.Content><Alert.Description>{error}</Alert.Description></Alert.Content></Alert>
                </div>}
                {status}
                {modal ? <Modal.Footer className="flex flex-wrap gap-2">{navigation}</Modal.Footer> :
                    <div className="mt-6 xl:mt-0 xl:left-8 xl:right-8 xl:absolute xl:bottom-8">{navigation}</div>}
            </Form>;
        }}
    </Formik>;
};

export default Wizard;
