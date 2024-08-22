import {Theme} from "Frontend/theming/theme";
import {Tooltip} from "@nextui-org/react";

export default function ThemePreview({theme, mode, isSelected}: {
    theme: Theme,
    mode: "light" | "dark",
    isSelected?: boolean
}) {
    return (
        <Tooltip content={<p className="capitalize">{theme.name?.replace("-", " ")}</p>} placement="bottom">
            <div className={`
                ${theme.name}-${mode}
                bg-primary p-6 border-2 rounded-full
                ${isSelected ? "border-foreground" : "border-foreground-200 hover:border-focus"}`}
            />
        </Tooltip>
    );
}