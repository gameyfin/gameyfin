import router from 'Frontend/routes.js';
import {AuthProvider} from 'Frontend/util/auth.js';
import {RouterProvider} from 'react-router-dom';
import "./main.css";
import {IconContext} from "@phosphor-icons/react";
import {StrictMode} from "react";
import {ThemeProvider} from "Frontend/@/components/theme-provider";

export default function App() {
    return (
        <StrictMode>
            <AuthProvider>
                <ThemeProvider
                    attribute="class"
                    defaultTheme="system"
                    enableSystem
                >
                    <IconContext.Provider value={{size: 20}}>
                        <RouterProvider router={router}/>
                    </IconContext.Provider>
                </ThemeProvider>
            </AuthProvider>
        </StrictMode>
    );
}
