import {protectRoutes} from '@vaadin/hilla-react-auth';
import {createBrowserRouter, RouteObject} from 'react-router-dom';
import LoginView from "Frontend/views/LoginView";
import MainLayout from "Frontend/views/MainLayout";
import TestView from "Frontend/views/TestView";
import SetupView from "Frontend/views/SetupView";
import ProfileView from "Frontend/views/ProfileView";
import {ThemeSelector} from "Frontend/components/theming/ThemeSelector";
import App from "Frontend/App";
import AdministrationView from "Frontend/views/AdministrationView";
import {LibraryManagement} from "Frontend/components/administration/LibraryManagement";
import {UserManagement} from "Frontend/components/administration/UserManagement";
import ProfileManagement from "Frontend/components/administration/ProfileManagement";

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
                        path: 'settings',
                        element: <ProfileView/>,
                        children: [
                            {path: 'profile', element: <ProfileManagement/>},
                            {path: 'appearance', element: <ThemeSelector/>}
                        ]
                    },
                    {
                        path: 'administration',
                        element: <AdministrationView/>,
                        children: [
                            {path: 'libraries', element: <LibraryManagement/>},
                            {path: 'users', element: <UserManagement/>},
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
