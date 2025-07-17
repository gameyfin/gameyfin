import LoginView from "Frontend/views/LoginView";
import MainLayout from "Frontend/views/MainLayout";
import HomeView from "Frontend/views/HomeView";
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
import {SystemManagement} from "Frontend/components/administration/SystemManagement";
import GameView from "Frontend/views/GameView";
import LibraryManagementView from "Frontend/views/LibraryManagementView";
import SearchView from "Frontend/views/SearchView";
import RecentlyAddedView from "Frontend/views/RecentlyAddedView";
import LibraryView from "Frontend/views/LibraryView";
import {RouterConfigurationBuilder} from "@vaadin/hilla-file-router/runtime.js";

export const {router, routes} = new RouterConfigurationBuilder()
    .withReactRoutes([
        {
            element: <App/>,
            children: [
                {
                    element: <MainLayout/>,
                    children: [
                        {
                            index: true,
                            element: <HomeView/>
                        },
                        {
                            path: 'search',
                            element: <SearchView/>
                        },
                        {
                            path: 'recently-added',
                            element: <RecentlyAddedView/>
                        },
                        {
                            path: 'library/:libraryId',
                            element: <LibraryView/>
                        },
                        {
                            path: 'game/:gameId',
                            element: <GameView/>
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
                                {
                                    path: 'libraries',
                                    element: <LibraryManagement/>
                                },
                                {
                                    path: 'libraries/library/:libraryId',
                                    element: <LibraryManagementView/>
                                },
                                {path: 'users', element: <UserManagement/>},
                                {path: 'sso', element: <SsoManagement/>},
                                {path: 'messages', element: <MessageManagement/>},
                                {path: 'plugins', element: <PluginManagement/>},
                                {path: 'logs', element: <LogManagement/>},
                                {path: 'system', element: <SystemManagement/>}
                            ]
                        }
                    ]
                },
                {
                    path: 'login', element: <LoginView/>
                },
                {
                    path: 'setup', element: <SetupView/>
                },
                {
                    path: 'accept-invitation', element: <InvitationRegistrationView/>
                },
                {
                    path: 'reset-password', element: <PasswordResetView/>
                },
                {
                    path: 'confirm-email', element: <EmailConfirmationView/>
                },
            ]
        }
    ])
    .protect("/login")
    .build();
