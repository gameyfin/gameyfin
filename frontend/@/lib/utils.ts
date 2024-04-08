import {type ClassValue, clsx} from "clsx"
import {twMerge} from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
    return twMerge(clsx(inputs))
}

export function cssVar(variable: string) {
    return getComputedStyle(document.documentElement).getPropertyValue(`--${variable}`);
}

export function hsl(hsl: string) {
    return `hsl(${hsl}`;
}

export function rand(min: number, max: number) {
    const minCeiled = Math.ceil(min);
    const maxFloored = Math.floor(max);
    return Math.floor(Math.random() * (maxFloored - minCeiled) + minCeiled);
}
