import reactSwc from '@vitejs/plugin-react-swc';
import type {UserConfigFn} from 'vite';
import {overrideVaadinConfig} from './vite.generated';
import path from "path";

const customConfig: UserConfigFn = (env) => ({
    // Here you can add custom Vite parameters
    // https://vitejs.dev/config/
    plugins: [
        reactSwc({
            tsDecorators: true,
        }),
    ],
    resolve: {
        alias: {
            "@": path.resolve(__dirname, "./frontend"),
        },
    },
});

export default overrideVaadinConfig(customConfig);
