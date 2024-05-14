import {GameyfinClassic} from "./themes/gameyfin-classic";
import {GameyfinBlue} from "./themes/gameyfin-blue";
import {GameyfinViolet} from "./themes/gameyfin-violet";
import {Purple} from "./themes/purple";
import {Neutral} from "./themes/neutral";
import {Slate} from "./themes/slate";
import {Red} from "./themes/red";
import {Rose} from "./themes/rose";
import {Blue} from "./themes/blue";
import {Yellow} from "./themes/yellow";
import {Violet} from "./themes/violet";
import {Orange} from "./themes/orange";
import {Theme} from "./theme";
import {ConfigTheme, ConfigThemes} from "@nextui-org/react";


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

export const themes: Theme[] = [GameyfinBlue, GameyfinViolet, GameyfinClassic, Neutral, Slate, Red, Rose, Orange, Purple, Blue, Yellow, Violet];
