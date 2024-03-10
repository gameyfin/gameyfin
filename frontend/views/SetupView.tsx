import {Button, Card, Spinner, Step, Stepper, Typography} from "@material-tailwind/react";
import {useState} from "react";
import {ArrowLeft, ArrowRight, Check, GearFine, HandWaving, User} from "@phosphor-icons/react";
import {Form, Formik} from "formik";
import * as Yup from 'yup';
import Input from "Frontend/components/Input";

export default function SetupView() {
    const [activeStep, setActiveStep] = useState(0);
    const [isLastStep, setIsLastStep] = useState(false);
    const [isFirstStep, setIsFirstStep] = useState(false);
    const [isLoading, setLoading] = useState(false);
    const [isNextDisabled, setIsNextDisabled] = useState(false);

    const steps = [<WelcomeStep/>, <UserStep/>, <SettingsStep/>];

    const handleNext = () => !isLastStep && setActiveStep((cur) => cur + 1);
    const handlePrev = () => !isFirstStep && setActiveStep((cur) => cur - 1);
    const finish = () => {
        alert("Setup finished");
    }

    function WelcomeStep() {
        return (
            <div className="flex flex-col size-full gap-12 items-center">
                <Typography variant="h4">Welcome to Gameyfin 👋</Typography>
                <p className="place-content-center text-justify">
                    Gameyfin is a cutting-edge software tailored for gamers seeking efficient management of their
                    video
                    game collections. <br/><br/> With its intuitive interface and comprehensive features, Gameyfin
                    simplifies the organization of game libraries. Users can effortlessly add games through manual
                    input
                    or
                    automated recognition, categorize them based on various criteria like genre or platform, track
                    in-game
                    progress, and share achievements with friends. <br/><br/> Notably, Gameyfin stands out for its
                    user-friendly
                    design and adaptability, offering ample customization options to meet diverse user preferences.
                </p>
                <Typography variant="h5">Let's get started!</Typography>
            </div>
        );
    }

    function UserStep() {
        return (
            <div className="flex flex-col size-full gap-12 items-center">
                <Typography variant="h4">Create your account</Typography>
                <Typography className="-mt-8">This will set up the initial admin user account.</Typography>
                <Formik
                    validateOnMount
                    initialValues={{username: '', password: '', passwordRepeat: ''}}
                    validationSchema={Yup.object({
                        username: Yup.string()
                            .required('Required'),
                        password: Yup.string()
                            .min(8, 'Password must be at least 8 characters long')
                            .required('Required'),
                        passwordRepeat: Yup.string()
                            .equals([Yup.ref('password')], 'Passwords do not match')
                            .required('Required')
                    })}
                    onSubmit={(values, {setSubmitting}) => {
                        setTimeout(() => {
                            alert(JSON.stringify(values, null, 2));
                            setSubmitting(false);
                        }, 400);
                    }}
                >
                    {formik =>
                        <Form className="mb-1 flex flex-col w-full gap-6">
                            <Input
                                label="Username"
                                name="username"
                                type="text"
                            />
                            <Input
                                label="Password"
                                name="password"
                                type="password"
                            />
                            <Input
                                label="Password (repeat)"
                                name="passwordRepeat"
                                type="password"
                            />
                            <Button
                                type="submit"
                                disabled={!formik.isValid || formik.isSubmitting}
                                className="justify-center"
                            >
                                {formik.isSubmitting ?
                                    <Spinner className="h-4 w-full"/> : "Submit"
                                }
                            </Button>
                        </Form>
                    }
                </Formik>
            </div>
        );
    }

    function SettingsStep() {
        return (
            <>
            </>
        );
    }

    return (
        <div className="flex h-screen">
            <div className="fixed size-full bg-gradient-to-br from-gf-primary to-gf-secondary"></div>
            <Card className="w-3/4 h-3/4 min-w-[500px] m-auto p-8" shadow={true}>
                <div className="w-full mb-8">
                    <Stepper
                        activeStep={activeStep}
                        isLastStep={(value) => setIsLastStep(value)}
                        isFirstStep={(value) => setIsFirstStep(value)}
                    >
                        <Step onClick={() => setActiveStep(0)}>
                            <HandWaving/>
                        </Step>
                        <Step onClick={() => setActiveStep(1)}>
                            <User/>
                        </Step>
                        <Step onClick={() => setActiveStep(2)}>
                            <GearFine/>
                        </Step>
                    </Stepper>
                </div>
                <div className="flex flex-grow justify-center">
                    <div className="size-full px-8 min-w-[500px] md:w-1/3 md:px-0">
                        {steps[activeStep]}
                    </div>
                </div>
                <div className="bottom-0 w-full">
                    <div className="flex justify-between">
                        <Button onClick={handlePrev} disabled={isFirstStep} className="rounded-full">
                            <ArrowLeft/>
                        </Button>
                        <Button disabled={isNextDisabled} onClick={isLastStep ? finish : handleNext}
                                className="rounded-full">
                            {isLoading ? <Spinner/> : isLastStep ? <Check/> : <ArrowRight/>}
                        </Button>
                    </div>
                </div>
            </Card>
        </div>
    );
}