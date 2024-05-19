import {protectRoutes} from '@hilla/react-auth';
import {createBrowserRouter, RouteObject} from 'react-router-dom';
import LoginView from "Frontend/views/LoginView";
import MainLayout from "Frontend/views/MainLayout";
import TestView from "Frontend/views/TestView";
import SetupView from "Frontend/views/SetupView";
import ProfileView from "Frontend/views/ProfileView";
import {ThemeSelector} from "Frontend/components/theming/ThemeSelector";
import App from "Frontend/App";

export const routes = protectRoutes([
    {
        element: <App/>,
        handle: {requiresLogin: false},
        children: [
            {
                element: <MainLayout/>,
                handle: {requiresLogin: true},
                children: [
                    {
                        index: true, element: <TestView/>
                    },
                    {
                        path: 'profile',
                        element: <ProfileView/>,
                        children: [
                            {path: 'appearance', element: <ThemeSelector/>}
                        ]
                    }
                ]
            },
            {
                path: '/login', element: <LoginView/>, handle: {requiresLogin: false}
            },
            {
                path: '/setup', element: <SetupView/>, handle: {requiresLogin: false}
            }
        ],
    }
]) as RouteObject[];

export default createBrowserRouter(routes);
