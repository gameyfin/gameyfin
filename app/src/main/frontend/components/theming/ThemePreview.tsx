import {Theme} from "Frontend/theming/theme";
import {ColorSwatchPicker} from "@heroui/react";

export default function ThemePreview({theme}: {
    theme: Theme
}) {
    return (
        // Scoped to the theme's own class so --gf-secondary/--success/--warning/--danger
        // resolve to that theme's palette, independent of the currently applied theme.
        <ColorSwatchPicker.Item
            key={theme.name}
            color={theme.colors.accent}
            aria-label={theme.name}
            className={`${theme.name} size-[10vh] min-h-[50px] min-w-[50px] aspect-square overflow-hidden`}
        >
            <div className="flex flex-col size-full">
                <ColorSwatchPicker.Swatch className="flex-1 rounded-none!"/>
                <div className="basis-1/4 flex flex-row">
                    <div className="flex-1 bg-[var(--gf-secondary)]"/>
                    <div className="flex-1 bg-success"/>
                    <div className="flex-1 bg-warning"/>
                    <div className="flex-1 bg-danger"/>
                </div>
            </div>
            <ColorSwatchPicker.Indicator/>
        </ColorSwatchPicker.Item>
    );
}

