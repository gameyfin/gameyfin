import withMT from "@material-tailwind/react/utils/withMT";
import {withMaterialColors} from "tailwind-material-colors";

/** @type {import('tailwindcss').Config} */
export default withMaterialColors(withMT({
        content: ["./frontend/index.html", "./frontend/**/*.{js,ts,jsx,tsx}"],
        theme: {
            extend: {
                colors: {
                    'gf-primary': '#2332c8',
                    'gf-secondary': '#6441a5'
                },
            }
        },
        plugins: [],
    }),
    {
        primary: "#2332c8"
    }
);