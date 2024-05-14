import {Config} from "tailwindcss/types/config";
import {nextui} from "@nextui-org/react";
import {NextUIConfig} from "./nextui";
import withMT from "@material-tailwind/react/utils/withMT";

export default withMT({
    darkMode: "class",
    content: [
        './frontend/index.html',
        './frontend/**/*.{js,ts,jsx,tsx}',
        './node_modules/@nextui-org/theme/dist/**/*.{js,ts,jsx,tsx}'
    ],
    theme: {
        extend: {
            colors: {
                'gf-primary': '#2332c8',
                'gf-secondary': '#6441a5'
            }
        }
    },
    plugins: [
        nextui(NextUIConfig)
    ],
} satisfies Config);