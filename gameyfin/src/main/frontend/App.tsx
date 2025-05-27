import {Outlet, useHref, useNavigate} from 'react-router';
import "./main.css";
import "Frontend/util/custom-validators";
import {HeroUIProvider} from "@heroui/react";
import {ToastProvider} from "@heroui/toast";
import {ThemeProvider as NextThemesProvider} from "next-themes";
import {themeNames} from "Frontend/theming/themes";
import {AuthProvider} from "Frontend/util/auth";
import {IconContext, X} from "@phosphor-icons/react";
import client from "Frontend/generated/connect-client.default";
import {ErrorHandlingMiddleware} from "Frontend/util/middleware";
import {initializeLibraryState} from "Frontend/state/LibraryState";
import {initializeGameState} from "Frontend/state/GameState";
import {initializeScanState} from "Frontend/state/ScanState";

export default function App() {
    const navigate = useNavigate();

    client.middlewares = [ErrorHandlingMiddleware];

    initializeLibraryState();
    initializeGameState();
    initializeScanState();

    return (
        <HeroUIProvider className="size-full" navigate={navigate} useHref={useHref}>
            <NextThemesProvider attribute="class" themes={themeNames()} defaultTheme="gameyfin-violet-dark">
                <AuthProvider>
                    <IconContext.Provider value={{size: 20}}>
                        <Outlet/>
                        <ToastProvider
                            toastProps={{
                                shouldShowTimeoutProgress: true,
                                radius: "sm",
                                variant: "flat",
                                hideIcon: true,
                                closeIcon: <X/>,
                                classNames: {
                                    closeButton: "opacity-100 absolute right-4 top-1/2 -translate-y-1/2",
                                    progressTrack: "h-1",
                                }
                            }}
                            toastOffset={64}
                        />
                    </IconContext.Provider>
                </AuthProvider>
            </NextThemesProvider>
        </HeroUIProvider>
    );
}
