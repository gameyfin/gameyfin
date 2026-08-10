import {Outlet, useHref, useNavigate} from 'react-router';
import "./main.css";
import {RouterProvider, Toast} from "@heroui/react";
import {ThemeProvider as NextThemesProvider} from "next-themes";
import {injectThemeStyles, themeNames} from "Frontend/theming/themes";
import {AuthProvider, useAuth} from "Frontend/util/auth";
import {IconContext} from "@phosphor-icons/react";
import client from "Frontend/generated/connect-client.default";
import {ErrorHandlingMiddleware} from "Frontend/util/middleware";
import {initializeLibraryState} from "Frontend/state/LibraryState";
import {initializeGameState} from "Frontend/state/GameState";
import {initializeScanState} from "Frontend/state/ScanState";
import {initializePluginState} from "Frontend/state/PluginState";
import {isAdmin} from "Frontend/util/utils";
import {useRouteMetadata} from "Frontend/util/routing";
import {useCallback, useEffect} from "react";
import {initializeGameRequestState} from "Frontend/state/GameRequestState";
import {initializePlatformState} from "Frontend/state/PlatformState";
import {initializeDownloadSessionState} from "Frontend/state/DownloadSessionState";
import {initializeUserState} from "Frontend/state/UserState";
import {initializeCollectionState} from "Frontend/state/CollectionState";

// Generate & inject the per-theme CSS custom property overrides once, before first render
injectThemeStyles();

export default function App() {
    client.middlewares = [ErrorHandlingMiddleware];

    const navigate = useNavigate();
    const reactRouterUseHref = useHref;
    // Fixes an issue where external links would be treated as internal links
    const safeUseHref = useCallback(
        (href: string) => /^https?:\/\//i.test(href) ? href : reactRouterUseHref(href),
        [reactRouterUseHref]
    );
    const routeMetadata = useRouteMetadata();

    useEffect(() => {
        document.title = routeMetadata?.title ?? "Gameyfin";
    }, [routeMetadata, window.location.href]);

    return (
        <RouterProvider navigate={navigate} useHref={safeUseHref}>
            <div className="size-full">
                <NextThemesProvider attribute="class" themes={themeNames()} defaultTheme="gameyfin-violet-dark">
                    <AuthProvider>
                        <ViewWithAuth/>
                    </AuthProvider>
                </NextThemesProvider>
            </div>
        </RouterProvider>
    );
}

function ViewWithAuth() {
    const auth = useAuth();

    useEffect(() => {
        if (auth.state.initializing || auth.state.loading) return;

        initializeLibraryState();
        initializeCollectionState();
        initializePlatformState();
        initializeGameRequestState();
        initializePluginState();
        initializeGameState();

        if (isAdmin(auth)) {
            initializeScanState();
            initializeDownloadSessionState();
            initializeUserState();
        }
    }, [auth]);

    return <>
        <IconContext.Provider value={{size: 20}}>
            <Outlet/>
            <Toast.Provider placement="bottom end"/>
        </IconContext.Provider>
    </>;
}