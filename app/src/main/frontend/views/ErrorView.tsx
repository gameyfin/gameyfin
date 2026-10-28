import {Button} from "@heroui/react";
import {ArrowClockwiseIcon, GameControllerIcon, IconContext} from "@phosphor-icons/react";
import {ThemeProvider} from "next-themes";
import {useHref} from "react-router";
import GameyfinLogo from "Frontend/components/theming/GameyfinLogo";
import IconBackgroundPattern from "Frontend/components/general/IconBackgroundPattern";

export default function ErrorView() {
    const homeHref = useHref('/');

    // The route error boundary replaces App, including its theme provider.
    return (
        <ThemeProvider attribute="class" defaultTheme="dark">
            <main className="relative isolate flex min-h-full flex-col items-center justify-center gap-4 overflow-hidden p-6 text-center">
                <div aria-hidden="true" className="pointer-events-none absolute inset-0 -z-10">
                    <IconBackgroundPattern/>
                </div>
                <GameyfinLogo className="h-10 fill-foreground mb-4"/>
                <h1 className="text-4xl font-bold">Unexpected Plot Twist!</h1>
                <p className="max-w-xl text-lg">
                    Something went wrong while loading this page. Try again, or return to the main menu.
                </p>
                <IconContext.Provider value={{size: 20, weight: "fill"}}>
                    <div className="mt-4 flex flex-wrap justify-center gap-4">
                        <Button variant="primary" size="lg" onPress={() => window.location.reload()}>
                            <ArrowClockwiseIcon/>
                            Retry mission
                        </Button>
                        <Button variant="secondary" size="lg" onPress={() => window.location.assign(homeHref)}>
                            <GameControllerIcon/>
                            Return to the main menu
                        </Button>
                    </div>
                </IconContext.Provider>
            </main>
        </ThemeProvider>
    );
}
