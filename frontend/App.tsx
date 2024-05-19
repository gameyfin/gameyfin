import {Outlet, useNavigate} from 'react-router-dom';
import "./main.css";
import {NextUIProvider} from "@nextui-org/react";
import {ThemeProvider as NextThemesProvider} from "next-themes";
import {themeNames} from "Frontend/theming/themes";
import {AuthProvider} from "Frontend/util/auth";
import {IconContext} from "@phosphor-icons/react";

export default function App() {
    const navigate = useNavigate();

    return (
        <NextUIProvider className="size-full" navigate={navigate}>
            <NextThemesProvider attribute="class" themes={themeNames()} defaultTheme="gameyfin-violet-dark">
                <AuthProvider>
                    <IconContext.Provider value={{size: 20}}>
                        <Outlet/>
                    </IconContext.Provider>
                </AuthProvider>
            </NextThemesProvider>
        </NextUIProvider>
    );
}
