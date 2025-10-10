import {heroui, HeroUIPluginConfig} from "@heroui/react";
import {compileThemes, themes} from "./theming/themes"

export const HeroUIConfig: HeroUIPluginConfig = {
    // TODO: Prefix disabled until bug in heroui is fixed: https://github.com/heroui-inc/heroui/issues/5403
    // prefix: "gf",
    themes: compileThemes(themes)
};

export default heroui(HeroUIConfig);