import type {ComponentProps} from "react";
import React from "react";
import type {ButtonProps} from "@heroui/react";
import {cn} from "@heroui/react";
import {useControlledState} from "@react-stately/utils";
import {domAnimation, LazyMotion, m} from "framer-motion"; // reintroduce LazyMotion & domAnimation

export type StepDescriptor = {
    title?: React.ReactNode;
    icon?: React.ReactNode;
    className?: string;
};

export interface StepperProps extends React.HTMLAttributes<HTMLButtonElement> {
    steps?: StepDescriptor[];
    color?: ButtonProps["color"];
    currentStep?: number;
    defaultStep?: number;
    hideProgressBars?: boolean;
    className?: string;
    stepClassName?: string;
    onStepChange?: (stepIndex: number) => void;
    allowFutureNavigation?: boolean;
}

function CheckIcon(props: ComponentProps<"svg">) {
    return (
        <svg {...props} fill="none" stroke="currentColor" strokeWidth={2} viewBox="0 0 24 24">
            <m.path
                animate={{pathLength: 1}}
                d="M5 13l4 4L19 7"
                initial={{pathLength: 0}}
                strokeLinecap="round"
                strokeLinejoin="round"
                transition={{
                    delay: 0.2,
                    type: "tween",
                    ease: "easeOut",
                    duration: 0.3,
                }}
            />
        </svg>
    );
}

const Stepper = React.forwardRef<HTMLButtonElement, StepperProps>(
    (
        {
            color = "primary",
            steps = [],
            defaultStep = 0,
            onStepChange,
            currentStep: currentStepProp,
            hideProgressBars = false,
            stepClassName,
            className,
            allowFutureNavigation = false,
            ...props
        },
        ref
    ) => {
        const [currentStep, setCurrentStep] = useControlledState(currentStepProp, defaultStep, onStepChange);

        const colors = React.useMemo(() => {
            let userColor;
            let fgColor;
            const colorsVars = [
                "[--active-fg-color:var(--step-fg-color)]",
                "[--active-border-color:var(--step-color)]",
                "[--active-color:var(--step-color)]",
                "[--complete-background-color:var(--step-color)]",
                "[--complete-border-color:var(--step-color)]",
                "[--inactive-border-color:hsl(var(--heroui-default-300))]",
                "[--inactive-color:hsl(var(--heroui-default-300))]"
            ];
            switch (color) {
                case "secondary":
                    userColor = "[--step-color:hsl(var(--heroui-secondary))]";
                    fgColor = "[--step-fg-color:hsl(var(--heroui-secondary-foreground))]";
                    break;
                case "success":
                    userColor = "[--step-color:hsl(var(--heroui-success))]";
                    fgColor = "[--step-fg-color:hsl(var(--heroui-success-foreground))]";
                    break;
                case "warning":
                    userColor = "[--step-color:hsl(var(--heroui-warning))]";
                    fgColor = "[--step-fg-color:hsl(var(--heroui-warning-foreground))]";
                    break;
                case "danger":
                    userColor = "[--step-color:hsl(var(--heroui-error))]";
                    fgColor = "[--step-fg-color:hsl(var(--heroui-error-foreground))]";
                    break;
                case "default":
                    userColor = "[--step-color:hsl(var(--heroui-default))]";
                    fgColor = "[--step-fg-color:hsl(var(--heroui-default-foreground))]";
                    break;
                case "primary":
                default:
                    userColor = "[--step-color:hsl(var(--heroui-primary))]";
                    fgColor = "[--step-fg-color:hsl(var(--heroui-primary-foreground))]";
                    break;
            }
            if (!className?.includes("--step-fg-color")) colorsVars.unshift(fgColor);
            if (!className?.includes("--step-color")) colorsVars.unshift(userColor);
            if (!className?.includes("--inactive-bar-color"))
                colorsVars.push("[--inactive-bar-color:hsl(var(--heroui-default-300))]");
            return colorsVars;
        }, [color, className]);

        // Compute statuses once
        const statuses = steps.map((_, i) => (
            currentStep === i ? "active" : currentStep < i ? "inactive" : "complete"
        ));

        return (
            <LazyMotion features={domAnimation}> {/* enable pathLength & variants animations */}
                <nav aria-label="Progress" className={cn("relative w-full overflow-x-visible py-4", colors, className)}>
                    {/* Circles + connectors row */}
                    <div className="flex w-full items-center">
                        {steps.map((step, idx) => {
                            const status = statuses[idx];
                            const canNavigate = allowFutureNavigation || idx <= currentStep;
                            const isLast = idx === steps.length - 1;
                            return (
                                <div key={idx}
                                     className={cn("flex items-center", !isLast && "flex-1")}> {/* flex-1 only if there is a connector after */}
                                    <button
                                        ref={ref}
                                        aria-current={status === "active" ? "step" : undefined}
                                        type="button"
                                        onClick={() => canNavigate && setCurrentStep(idx)}
                                        className={cn(
                                            "group relative flex h-[38px] w-[38px] items-center justify-center rounded-full border-medium font-semibold text-large bg-content1 transition-colors duration-300",
                                            !canNavigate && "pointer-events-none opacity-60",
                                            step.className,
                                            stepClassName,
                                            status === "inactive" && "text-(--inactive-color) border-(--inactive-border-color)",
                                            status === "active" && "text-(--active-color) border-(--active-border-color)",
                                            status === "complete" && "border-(--complete-border-color) bg-(--complete-background-color) shadow-lg"
                                        )}
                                        {...props}
                                    >
                                        <m.div
                                            animate={status}
                                            initial={false}
                                            variants={{
                                                inactive: {scale: 1, opacity: 0.85},
                                                active: {scale: 1.04, opacity: 1},
                                                complete: {scale: 1, opacity: 1}
                                            }}
                                            transition={{type: "spring", stiffness: 260, damping: 20}}
                                            className="flex items-center justify-center w-full h-full"
                                        >
                                            {status === "complete" ? (
                                                <CheckIcon key={`check-${idx}`}
                                                           className="h-6 w-6 text-(--active-fg-color)"/>
                                            ) : step.icon ? (
                                                step.icon
                                            ) : (
                                                <span>{idx + 1}</span>
                                            )}
                                        </m.div>
                                    </button>
                                    {!isLast && !hideProgressBars && (
                                        <div className="flex-1">
                                            <div
                                                className="mx-3 h-0.5 rounded-full bg-(--inactive-bar-color) relative">{/* gap so line does not touch circles */}
                                                <m.div
                                                    className="absolute left-0 top-0 h-full rounded-full bg-(--active-border-color)"
                                                    animate={{width: idx < currentStep ? '100%' : 0}}
                                                    transition={{duration: 0.35, ease: 'easeInOut'}}
                                                />
                                            </div>
                                        </div>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                    {/* Titles row */}
                    <div className={cn("mt-2 grid w-full")}
                         style={{gridTemplateColumns: `repeat(${steps.length}, minmax(0,1fr))`}}>
                        {steps.map((step, idx) => {
                            const status = statuses[idx];
                            return (
                                <div key={idx} className="flex justify-center px-1 text-center">
                                    {step.title && (
                                        <span
                                            className={cn(
                                                "text-small lg:text-medium font-medium transition-[color,opacity] duration-300",
                                                status === "inactive" ? "text-default-500" : "text-default-foreground"
                                            )}
                                        >
                                            {step.title}
                                        </span>
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </nav>
            </LazyMotion>
        );
    }
);

Stepper.displayName = "Stepper";
export default Stepper;
