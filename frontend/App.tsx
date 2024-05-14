import router from 'Frontend/routes.js';
import {AuthProvider} from 'Frontend/util/auth.js';
import {RouterProvider} from 'react-router-dom';
import "./main.css";
import {IconContext} from "@phosphor-icons/react";
import {StrictMode} from "react";
import {NextUIProvider} from "@nextui-org/react";
import {ThemeProvider as NextThemesProvider} from "next-themes";
import {themeNames} from "Frontend/theming/themes";

export default function App() {
    return (
        <StrictMode>
            <NextUIProvider className="size-full">
                <NextThemesProvider attribute="class" defaultTheme="green-light" themes={themeNames()}>
                    <AuthProvider>
                        <IconContext.Provider value={{size: 20}}>
                            <RouterProvider router={router}/>
                        </IconContext.Provider>
                    </AuthProvider>
                </NextThemesProvider>
            </NextUIProvider>
        </StrictMode>
    );
}
