import {Button} from "@heroui/react";
import {useNavigate} from "react-router";
import { AlienIcon, CompassIcon, CubeIcon, DiceFiveIcon, FlagCheckeredIcon, GameControllerIcon, GhostIcon, Icon, IconContext, JoystickIcon, MagicWandIcon, PuzzlePieceIcon, RocketLaunchIcon, SkullIcon, SmileyXEyesIcon, SwordIcon } from "@phosphor-icons/react";
import React, {ReactElement, useState} from "react";
import GameyfinLogo from "Frontend/components/theming/GameyfinLogo";
import IconBackgroundPattern from "Frontend/components/general/IconBackgroundPattern";

type ErrorText = {
    title: string;
    subtitle: string;
    buttonText: string;
    icon: ReactElement<Icon>;
}

export default function ErrorView() {
    const navigate = useNavigate();

    const errorTexts: ErrorText[] = [
        {
            "title": "404 – Level Not Found!",
            "subtitle": "You’ve wandered off the map. This level doesn’t exist—or maybe it’s still in development.",
            "buttonText": "Go back to the main menu",
            "icon": <JoystickIcon/>
        },
        {
            "title": "404 – Quest Failed",
            "subtitle": "The path you seek does not exist. Maybe it was just a side quest after all.",
            "buttonText": "Return to the guild hall",
            "icon": <CompassIcon/>
        },
        {
            "title": "404 – You’ve encountered a glitch in the system!",
            "subtitle": "The page you’re looking for couldn’t load. Don’t worry, no coins were lost.",
            "buttonText": "Retry mission",
            "icon": <AlienIcon/>
        },
        {
            "title": "404 – Game Cartridge Not Inserted",
            "subtitle": "This page failed to load. Did you blow on the cartridge and try again?",
            "buttonText": "Reset the console",
            "icon": <DiceFiveIcon/>
        },
        {
            "title": "404 – You are in the wrong zone…",
            "subtitle": "This area is off-limits… or was never meant to be explored. Tread carefully.",
            "buttonText": "Find a safe path",
            "icon": <SmileyXEyesIcon/>
        },
        {
            "title": "404 – You Missed the Jump!",
            "subtitle": "The platform you were trying to reach isn’t here. Maybe it was a hidden level?",
            "buttonText": "Respawn at Start",
            "icon": <GameControllerIcon/>
        },
        {
            "title": "404 – Signal Lost in Deep Space",
            "subtitle": "We've lost contact with this page. All we have is static and void.",
            "buttonText": "Return to Command Center",
            "icon": <RocketLaunchIcon/>
        },
        {
            "title": "404 – The Page Has Vanished in a Puff of Smoke",
            "subtitle": "A forbidden spell may have erased the page from existence. Try another path.",
            "buttonText": "Return to the Grimoire",
            "icon": <MagicWandIcon/>
        },
        {
            "title": "404 – Block Not Found",
            "subtitle": "The page you're looking for hasn't been crafted yet. Gather more resources and try again.",
            "buttonText": "Back to Base",
            "icon": <CubeIcon/>
        },
        {
            "title": "404 – Puzzle Piece Missing",
            "subtitle": "This page doesn’t quite fit. Try rotating it… or just go back.",
            "buttonText": "Solve a different puzzle",
            "icon": <PuzzlePieceIcon/>
        },
        {
            "title": "404 – You Took a Wrong Turn!",
            "subtitle": "You drifted off course and into the digital void.",
            "buttonText": "Return to the Starting Line",
            "icon": <FlagCheckeredIcon/>
        },
        {
            "title": "404 – This Page Didn’t Survive",
            "subtitle": "Only ruins remain. Whatever was here is long gone.",
            "buttonText": "Search for safe house",
            "icon": <SkullIcon/>
        },
        {
            "title": "404 – Instance Not Found",
            "subtitle": "This dungeon has been removed or doesn’t exist on this realm.",
            "buttonText": "Return to your stronghold",
            "icon": <SwordIcon/>
        },
        {
            "title": "404 – The Page Was… Never Really Here…",
            "subtitle": "You were warned not to look. But you clicked anyway.",
            "buttonText": "Turn Back Now",
            "icon": <GhostIcon/>
        }
    ];

    const [errorText] = useState<ErrorText>(errorTexts[Math.floor(Math.random() * errorTexts.length)]);

    return (
        <div className="flex flex-col gap-4 items-center justify-center h-full">
            <IconBackgroundPattern/>
            <GameyfinLogo className="h-10 fill-foreground mb-4"/>
            <h1 className="text-4xl font-bold">{errorText.title}</h1>
            <p className="text-lg">{errorText.subtitle}</p>
            <IconContext.Provider value={{size: 20, weight: "fill"}}>
                <Button startContent={errorText.icon}
                        color="primary"
                        size="lg"
                        className="mt-4"
                        onPress={() => navigate('/', {replace: true})}>
                    {errorText.buttonText}
                </Button>
            </IconContext.Provider>
        </div>
    );
}