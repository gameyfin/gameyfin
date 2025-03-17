import {Outlet, useHref, useNavigate} from 'react-router-dom';
import "./main.css";
import "Frontend/util/custom-validators";
import {HeroUIProvider} from "@heroui/react";
import {ThemeProvider as NextThemesProvider} from "next-themes";
import {themeNames} from "Frontend/theming/themes";
import {AuthProvider} from "Frontend/util/auth";
import {IconContext} from "@phosphor-icons/react";
import {Toaster} from "Frontend/@/components/ui/sonner";
import client from "Frontend/generated/connect-client.default";
import {ErrorHandlingMiddleware} from "Frontend/util/middleware";

export default function App() {
    const navigate = useNavigate();

    client.middlewares = [ErrorHandlingMiddleware];

    return (
        <HeroUIProvider className="size-full" navigate={navigate} useHref={useHref}>
            <NextThemesProvider attribute="class" themes={themeNames()} defaultTheme="gameyfin-violet-dark">
                <AuthProvider>
                    <IconContext.Provider value={{size: 20}}>
                        <Outlet/>
                        <Toaster/>
                    </IconContext.Provider>
                </AuthProvider>
            </NextThemesProvider>
        </HeroUIProvider>
    );
}
