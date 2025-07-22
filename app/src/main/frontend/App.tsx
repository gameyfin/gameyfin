import {Outlet, useHref, useNavigate} from 'react-router';
import "./main.css";
import {HeroUIProvider} from "@heroui/react";
import {ThemeProvider as NextThemesProvider} from "next-themes";
import {themeNames} from "Frontend/theming/themes";
import {AuthProvider, useAuth} from "Frontend/util/auth";
import {IconContext, X} from "@phosphor-icons/react";
import client from "Frontend/generated/connect-client.default";
import {ErrorHandlingMiddleware} from "Frontend/util/middleware";
import {initializeLibraryState} from "Frontend/state/LibraryState";
import {initializeGameState} from "Frontend/state/GameState";
import {initializeScanState} from "Frontend/state/ScanState";
import {ToastProvider} from "@heroui/toast";
import {initializePluginState} from "Frontend/state/PluginState";
import {isAdmin} from "Frontend/util/utils";
import {useRouteMetadata} from "Frontend/util/routing";
import {useEffect} from "react";

export default function App() {
    client.middlewares = [ErrorHandlingMiddleware];

    const navigate = useNavigate();
    const routeMetadata = useRouteMetadata();

    useEffect(() => {
        document.title = routeMetadata?.title ?? "Gameyfin";
    }, [routeMetadata, window.location.href]);

    return (
        <HeroUIProvider className="size-full" navigate={navigate} useHref={useHref}>
            <NextThemesProvider attribute="class" themes={themeNames()} defaultTheme="gameyfin-violet-dark">
                <AuthProvider>
                    <ViewWithAuth/>
                </AuthProvider>
            </NextThemesProvider>
        </HeroUIProvider>
    );
}

function ViewWithAuth() {
    const auth = useAuth();

    useEffect(() => {
        if (auth.state.initializing || auth.state.loading) return;

        initializeLibraryState();
        initializeGameState();

        if (isAdmin(auth)) {
            initializeScanState();
            initializePluginState();
        }
    }, [auth]);

    return <>
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
    </>;
}