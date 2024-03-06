import router from 'Frontend/routes.js';
import {AuthProvider} from 'Frontend/util/auth.js';
import {RouterProvider} from 'react-router-dom';
import "./main.css";
import {ThemeProvider} from "@material-tailwind/react";
import React from 'react';

export default function App() {
    return (
        <React.StrictMode>
            <ThemeProvider>
                <AuthProvider>
                    <RouterProvider router={router}/>
                </AuthProvider>
            </ThemeProvider>
        </React.StrictMode>
    );
}
