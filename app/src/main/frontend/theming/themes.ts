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

/**
 * v3 has no Tailwind plugin (unlike v2's `heroui()`), so themes can no longer be compiled into a
 * Tailwind config object. Instead, this generates a plain CSS string with one rule per theme/mode
 * combination that overrides HeroUI's CSS custom properties (`--accent`, `--accent-foreground`, ...).
 * The generated CSS is injected into the document via `injectThemeStyles()` (see below).
 */

/**
 * Pick a readable foreground (black/white) for a given hex background color, based on relative luminance.
 */
function readableForeground(hex: string): string {
    const c = hex.replace("#", "");
    const r = parseInt(c.substring(0, 2), 16) / 255;
    const g = parseInt(c.substring(2, 4), 16) / 255;
    const b = parseInt(c.substring(4, 6), 16) / 255;
    const luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b;
    return luminance > 0.55 ? "#000000" : "#ffffff";
}

function themeVariables(t: Theme): string {
    const accentForeground = readableForeground(t.colors.accent);
    const vars = [
        `--accent: ${t.colors.accent};`,
        `--accent-foreground: ${accentForeground};`,
        `--accent-hover: color-mix(in oklab, var(--accent) 90%, var(--accent-foreground) 10%);`,
        `--accent-soft: color-mix(in oklab, var(--accent) 15%, transparent);`,
        `--accent-soft-foreground: color-mix(in oklab, var(--accent) 70%, var(--foreground) 30%);`,
        `--accent-soft-hover: color-mix(in oklab, var(--accent) 20%, transparent);`,
        `--focus: var(--accent);`,
    ];

    if (t.colors.secondary) {
        const secondaryForeground = readableForeground(t.colors.secondary);
        vars.push(`--gf-secondary: ${t.colors.secondary};`, `--gf-secondary-foreground: ${secondaryForeground};`);
    }

    return vars.join("\n        ");
}

function ruleFor(t: Theme, mode: "light" | "dark"): string {
    return `
    .${t.name}-${mode} {
        ${themeVariables(t)}
    }`;
}

export function compileThemes(themes: Theme[]): string {
    return themes
        .map((t) => ruleFor(t, "light") + ruleFor(t, "dark"))
        .join("\n");
}

/**
 * Injects the generated theme CSS into the document as a single <style> tag.
 * Safe to call multiple times - it replaces the previous stylesheet contents.
 */
export function injectThemeStyles(): void {
    const id = "gameyfin-theme-styles";
    let styleTag = document.getElementById(id) as HTMLStyleElement | null;
    if (!styleTag) {
        styleTag = document.createElement("style");
        styleTag.id = id;
        document.head.appendChild(styleTag);
    }
    styleTag.textContent = compileThemes(themes);
}

export function themeNames(): string[] {
    return themes.flatMap((t) => [`${t.name}-light`, `${t.name}-dark`]);
}

export const themes: Theme[] = [GameyfinBlue, GameyfinViolet, GameyfinClassic, Neutral, Slate, Red, Rose, Orange, Pink, Blue, Yellow, Violet, Colorblind];
