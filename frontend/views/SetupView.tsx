import {Button, Card, Spinner, Step, Stepper, Typography} from "@material-tailwind/react";
import {useState} from "react";
import {ArrowLeft, ArrowRight, Check, GearFine, HandWaving, User} from "@phosphor-icons/react";

export default function SetupView() {
    const [activeStep, setActiveStep] = useState(0);
    const [isLastStep, setIsLastStep] = useState(false);
    const [isFirstStep, setIsFirstStep] = useState(false);
    const [isLoading, setLoading] = useState(false);

    const steps = [<WelcomeStep/>, <UserStep/>, <SettingsStep/>];

    const handleNext = () => !isLastStep && setActiveStep((cur) => cur + 1);
    const handlePrev = () => !isFirstStep && setActiveStep((cur) => cur - 1);
    const finish = () => {
        alert("Setup finished");
    }

    function WelcomeStep() {
        return (
            <div className="flex flex-col gap-12 w-full items-center">
                <Typography variant="h4">Welcome to Gameyfin 👋</Typography>
                <text className="w-1/3 min-w-[500px] text-justify">
                    Gameyfin is a cutting-edge software tailored for gamers seeking efficient management of their video
                    game collections. <br/><br/> With its intuitive interface and comprehensive features, Gameyfin
                    simplifies the organization of game libraries. Users can effortlessly add games through manual input
                    or
                    automated recognition, categorize them based on various criteria like genre or platform, track
                    in-game
                    progress, and share achievements with friends. <br/><br/> Notably, Gameyfin stands out for its
                    user-friendly
                    design and adaptability, offering ample customization options to meet diverse user preferences.
                </text>
                <Typography variant="h5">Let's get started!</Typography>
            </div>
        );
    }

    function UserStep() {
        return (
            <>
            </>
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
            <Card className="w-3/4 h-3/4 m-auto p-8" shadow={true}>
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
                    {steps[activeStep]}
                </div>
                <div className="bottom-0 w-full">
                    <div className="flex justify-between">
                        <Button onClick={handlePrev} disabled={isFirstStep} className="rounded-full">
                            <ArrowLeft/>
                        </Button>
                        <Button onClick={isLastStep ? finish : handleNext} className="rounded-full">
                            {isLoading ? <Spinner/> : isLastStep ? <Check/> : <ArrowRight/>}
                        </Button>
                    </div>
                </div>
            </Card>
        </div>
    );
}