import withMT from "@material-tailwind/react/utils/withMT";

/** @type {import('tailwindcss').Config} */
export default withMT({
    content: ["./frontend/index.html", "./frontend/**/*.{js,ts,jsx,tsx}"],
    theme: {
        extend: {
            colors: {
                'gf-primary': '#2332c8',
                'gf-secondary': '#6441a5'
            },
        },
    },
    plugins: [],
});