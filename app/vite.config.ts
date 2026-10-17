import type {UserConfigFn} from 'vite';
import {overrideVaadinConfig} from './vite.generated.ts';
import tailwindcss from "@tailwindcss/vite";

const customConfig: UserConfigFn = (env) => ({
    // Here you can add custom Vite parameters
    // https://vitejs.dev/config/
    plugins: [tailwindcss()]
});

export default overrideVaadinConfig(customConfig);
