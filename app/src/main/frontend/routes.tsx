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
import ErrorView from "Frontend/views/ErrorView";
import GameRequestView from "Frontend/views/GameRequestView";
import {GameRequestManagement} from "Frontend/components/administration/GameRequestManagement";
import {DownloadManagement} from "Frontend/components/administration/DownloadManagement";
import {UiManagement} from "Frontend/components/administration/UiManagement";

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
                            element: <SearchView/>,
                            handle: {title: 'Search'}
                        },
                        {
                            path: 'recently-added',
                            element: <RecentlyAddedView/>,
                            handle: {title: 'Recently Added'}
                        },
                        {
                            path: '/requests',
                            element: <GameRequestView/>,
                            handle: {title: 'Game requests'}
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
                            handle: {title: 'Profile'},
                            children: [
                                {
                                    path: 'profile',
                                    element: <ProfileManagement/>,
                                    handle: {title: 'Profile Settings'}
                                },
                                {
                                    path: 'appearance',
                                    element: <ThemeSelector/>,
                                    handle: {title: 'Appearance'}
                                }
                            ]
                        },
                        {
                            path: 'administration',
                            element: <AdministrationView/>,
                            handle: {title: 'Administration'},
                            children: [
                                {
                                    path: 'libraries',
                                    element: <LibraryManagement/>,
                                    handle: {title: 'Administration - Libraries'}
                                },
                                {
                                    path: 'libraries/library/:libraryId',
                                    element: <LibraryManagementView/>,
                                    handle: {title: 'Administration - Library'}
                                },
                                {
                                    path: 'ui',
                                    element: <UiManagement/>,
                                    handle: {title: 'Administration - UI Settings'}
                                },
                                {
                                    path: 'requests',
                                    element: <GameRequestManagement/>,
                                    handle: {title: 'Administration - Game Requests'}
                                },
                                {
                                    path: 'downloads',
                                    element: <DownloadManagement/>,
                                    handle: {title: 'Administration - Downloads'}
                                },
                                {
                                    path: 'users',
                                    element: <UserManagement/>,
                                    handle: {title: 'Administration - Users'}
                                },
                                {
                                    path: 'sso',
                                    element: <SsoManagement/>,
                                    handle: {title: 'Administration - SSO'}
                                },
                                {
                                    path: 'messages',
                                    element: <MessageManagement/>,
                                    handle: {title: 'Administration - Messages'}
                                },
                                {
                                    path: 'plugins',
                                    element: <PluginManagement/>,
                                    handle: {title: 'Administration - Plugins'}
                                },
                                {
                                    path: 'logs',
                                    element: <LogManagement/>,
                                    handle: {title: 'Administration - Logs'}
                                },
                                {
                                    path: 'system',
                                    element: <SystemManagement/>,
                                    handle: {title: 'Administration - System'}
                                }
                            ]
                        }
                    ]
                },
                {
                    path: 'login',
                    element: <LoginView/>,
                    handle: {title: 'Login'}
                },
                {
                    path: 'setup',
                    element: <SetupView/>,
                    handle: {title: 'Setup'}
                },
                {
                    path: 'accept-invitation',
                    element: <InvitationRegistrationView/>,
                    handle: {title: 'You have been invited to Gameyfin!'}
                },
                {
                    path: 'reset-password',
                    element: <PasswordResetView/>,
                    handle: {title: 'Reset Password'}
                },
                {
                    path: 'confirm-email',
                    element: <EmailConfirmationView/>,
                    handle: {title: 'Confirm Email'}
                },
                {
                    path: '*',
                    element: <ErrorView/>,
                    handle: {title: 'Error'}
                }
            ]
        }
    ])
    .protect("/login")
    .build();
