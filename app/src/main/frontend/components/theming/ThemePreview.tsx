import {Theme} from "Frontend/theming/theme";
import {Tooltip} from "@heroui/react";

export default function ThemePreview({theme, isSelected}: {
    theme: Theme,
    isSelected?: boolean
}) {
    return (
        <Tooltip content={<p className="capitalize">{theme.name?.replace("-", " ")}</p>} placement="bottom">
            <div className={`flex flex-col grow aspect-square border-2 rounded-large overflow-hidden
                ${theme.name}-dark
                ${isSelected ? "border-foreground" : "border-foreground-200 hover:border-focus"}`}>
                <div className="flex-1 bg-primary"/>
                <div className="basis-1/4 flex flex-row">
                    <div className="flex-1 bg-secondary"/>
                    <div className="flex-1 bg-success"/>
                    <div className="flex-1 bg-warning"/>
                    <div className="flex-1 bg-danger"/>
                </div>
            </div>
        </Tooltip>
    );
}