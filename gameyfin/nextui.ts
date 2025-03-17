import {NextUIPluginConfig} from "@heroui/react";
import {compileThemes, themes} from "./src/main/frontend/theming/themes"

export const NextUIConfig: NextUIPluginConfig = {
    prefix: "gf",
    themes: compileThemes(themes)
};