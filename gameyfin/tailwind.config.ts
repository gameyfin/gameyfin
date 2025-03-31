import {Config} from "tailwindcss/types/config";
import {heroui} from "@heroui/react";
import {HeroUIConfig} from "./heroui";
import withMT from "@material-tailwind/react/utils/withMT";

export default withMT({
    darkMode: "class",
    content: [
        './src/main/frontend/index.html',
        './src/main/frontend/**/*.{js,ts,jsx,tsx}',
        "./node_modules/@heroui/theme/dist/**/*.{js,ts,jsx,tsx}"
    ],
    theme: {
        extend: {
            colors: {
                'gf-primary': '#2332c8',
                'gf-secondary': '#6441a5'
            },
            gridTemplateColumns: {
                '300px': 'repeat(auto-fit, 300px)'
            }
        }
    },
    plugins: [
        heroui(HeroUIConfig)
    ],
} satisfies Config);