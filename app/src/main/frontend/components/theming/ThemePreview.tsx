import {Theme} from "Frontend/theming/theme";
import {Tooltip} from "@heroui/react";

export default function ThemePreview({theme, isSelected}: {
    theme: Theme,
    isSelected?: boolean
}) {
    return (
        <Tooltip>
            <Tooltip.Trigger>
                <div className={`flex flex-col grow aspect-square border-2 rounded-2xl overflow-hidden
                    ${theme.name}-dark
                    ${isSelected ? "border-foreground" : "border-border hover:border-focus"}`}>
                    <div className="flex-1 bg-accent"/>
                    <div className="basis-1/4 flex flex-row">
                        <div className="flex-1 bg-[var(--gf-secondary)]"/>
                        <div className="flex-1 bg-success"/>
                        <div className="flex-1 bg-warning"/>
                        <div className="flex-1 bg-danger"/>
                    </div>
                </div>
            </Tooltip.Trigger>
            <Tooltip.Content placement="bottom">
                <p className="capitalize">{theme.name?.replace("-", " ")}</p>
            </Tooltip.Content>
        </Tooltip>
    );
}