import {GameyfinClassic} from "./themes/gameyfin-classic";
import {GameyfinBlue} from "./themes/gameyfin-blue";
import {GameyfinViolet} from "./themes/gameyfin-violet";
import {Pink} from "./themes/pink";
import {Neutral} from "./themes/neutral";
import {Slate} from "./themes/slate";
import {Red} from "./themes/red";
import {Rose} from "./themes/rose";
import {Blue} from "./themes/blue";
import {Yellow} from "./themes/yellow";
import {Violet} from "./themes/violet";
import {Orange} from "./themes/orange";
import {Colorblind} from "./themes/colorblind";
import {Theme} from "./theme";
import {ConfigTheme, ConfigThemes} from "@heroui/react";


function light(c: Theme): ConfigTheme {
    let t: Theme = structuredClone(c);
    delete t.name;
    (t as ConfigTheme).extend = "light";
    return t;
}

function dark(c: Theme): ConfigTheme {
    let t: Theme = structuredClone(c);
    delete t.name;
    (t as ConfigTheme).extend = "dark";
    return t;
}

export function compileThemes(themes: Theme[]): ConfigThemes {
    let compiledThemes: any = {};

    themes.forEach((c: Theme) => {
        compiledThemes[`${c.name}-light`] = light(c);
        compiledThemes[`${c.name}-dark`] = dark(c);
    })

    return compiledThemes;
}

export function themeNames(): string[] {
    return Object.keys(compileThemes(themes));
}

export const themes: Theme[] = [GameyfinBlue, GameyfinViolet, GameyfinClassic, Neutral, Slate, Red, Rose, Orange, Pink, Blue, Yellow, Violet, Colorblind];
