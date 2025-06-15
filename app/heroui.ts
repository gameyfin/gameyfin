import {HeroUIPluginConfig} from "@heroui/react";
import {compileThemes, themes} from "./src/main/frontend/theming/themes"

export const HeroUIConfig: HeroUIPluginConfig = {
    prefix: "gf",
    themes: compileThemes(themes)
};