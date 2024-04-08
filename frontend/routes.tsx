import {protectRoutes} from '@hilla/react-auth';
import {createBrowserRouter, RouteObject} from 'react-router-dom';
import LoginView from "Frontend/views/LoginView";
import MainLayout from "Frontend/views/MainLayout";
import TestView from "Frontend/views/TestView";
import SetupView from "Frontend/views/SetupView";

export const routes = protectRoutes([
    {
        element: <MainLayout/>,
        handle: {requiresLogin: true},
        children: [
            {path: '/', element: <TestView/>, handle: {title: 'Gameyfin - Test'}},
        ],
    },
    {
        path: '/login', element: <LoginView/>, handle: {requiresLogin: false}
    },
    {
        path: '/setup', element: <SetupView/>, handle: {requiresLogin: false}
    }
]) as RouteObject[];

export default createBrowserRouter(routes);
