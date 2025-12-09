import {createRoot} from 'react-dom/client';
import {StrictMode} from "react";
import {RouterProvider} from "react-router";
import {router} from './routes';

const container = document.getElementById('outlet')!;
const root = createRoot(container);

declare module 'valtio' {
    function useSnapshot<T extends object>(p: T): T
}

root.render(
    <StrictMode>
        <RouterProvider router={router}/>
    </StrictMode>
);
