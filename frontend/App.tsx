import router from 'Frontend/routes.js';
import {AuthProvider} from 'Frontend/util/auth.js';
import {RouterProvider} from 'react-router-dom';
import "./main.css";
import {ThemeProvider} from "@material-tailwind/react";
import {IconContext} from "@phosphor-icons/react";
import {StrictMode} from "react";

export default function App() {
    return (
        <StrictMode>
            <AuthProvider>
                <ThemeProvider>
                    <IconContext.Provider value={{size: 20}}>
                        <RouterProvider router={router}/>
                    </IconContext.Provider>
                </ThemeProvider>
            </AuthProvider>
        </StrictMode>
    );
}
