import {protectRoutes} from '@hilla/react-auth';
import {createBrowserRouter, RouteObject} from 'react-router-dom';
import LoginView from "Frontend/views/LoginView";
import MainLayout from "Frontend/views/MainLayout";
import TestView from "Frontend/views/TestView";
import SetupView from "Frontend/views/SetupView";

export const routes = protectRoutes([
    {
        element: <MainLayout/>,
        handle: {title: 'Main', requiresLogin: true},
        children: [
            {path: '/', element: <TestView/>, handle: {title: 'Gameyfin', requiresLogin: true}},
        ],
    },
    {
        path: '/login',
        element: <LoginView/>
    },
    {
        path: '/setup',
        element: <SetupView/>
    }
]) as RouteObject[];

export default createBrowserRouter(routes);
