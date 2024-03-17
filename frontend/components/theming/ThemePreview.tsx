import {Theme} from "Frontend/components/theming/Theme";
import {Typography} from "@material-tailwind/react";

export default function ThemePreview({theme}: { theme: Theme }) {
    return (
        <div className={`
            size-full bg-background
            grid grid-rows-3
            rounded-lg
            border-2 border-on-background
            p-4 gap-4
            `}>
            <div className="bg-primary flex grow rounded-lg"></div>
            <div className="bg-secondary flex grow rounded-lg"></div>
            <div className="bg-tertiary flex grow rounded-lg"></div>
            <Typography variant="paragraph" className="text-center">{theme.name}</Typography>
        </div>
    );
}