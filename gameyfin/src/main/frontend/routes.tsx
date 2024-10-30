import {protectRoutes} from '@vaadin/hilla-react-auth';
import {createBrowserRouter, RouteObject} from 'react-router-dom';
import LoginView from "Frontend/views/LoginView";
import MainLayout from "Frontend/views/MainLayout";
import TestView from "Frontend/views/TestView";
import SetupView from "Frontend/views/SetupView";
import {ThemeSelector} from "Frontend/components/theming/ThemeSelector";
import App from "Frontend/App";
import {LibraryManagement} from "Frontend/components/administration/LibraryManagement";
import {UserManagement} from "Frontend/components/administration/UserManagement";
import ProfileManagement from "Frontend/components/administration/ProfileManagement";
import {SsoManagement} from "Frontend/components/administration/SsoManagement";
import {AdministrationView} from "Frontend/views/AdministrationView";
import {ProfileView} from "Frontend/views/ProfileView";
import {MessageManagement} from "Frontend/components/administration/MessageManagement";
import {LogManagement} from "Frontend/components/administration/LogManagement";
import PasswordResetView from "Frontend/views/PasswordResetView";
import EmailConfirmationView from "Frontend/views/EmailConfirmationView";
import InvitationRegistrationView from "Frontend/views/InvitationRegistrationView";
import PluginManagement from "Frontend/components/administration/PluginManagement";

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
                            {path: 'sso', element: <SsoManagement/>},
                            {path: 'messages', element: <MessageManagement/>},
                            {path: 'plugins', element: <PluginManagement/>},
                            {path: 'logs', element: <LogManagement/>}
                        ]
                    }
                ]
            },
            {
                path: '/login', element: <LoginView/>, handle: {requiresLogin: false}
            },
            {
                path: '/setup', element: <SetupView/>, handle: {requiresLogin: false}
            },
            {
                path: '/accept-invitation', element: <InvitationRegistrationView/>, handle: {requiresLogin: false}
            },
            {
                path: '/reset-password', element: <PasswordResetView/>, handle: {requiresLogin: false}
            },
            {
                path: '/confirm-email', element: <EmailConfirmationView/>, handle: {requiresLogin: true}
            },
        ],
    }
]) as RouteObject[];

export default createBrowserRouter(routes);
