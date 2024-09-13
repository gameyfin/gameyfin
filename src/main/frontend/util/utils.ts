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

export function roleToRoleName(role: string) {
    role = role.replace("ROLE_", "").toLowerCase();
    return role.charAt(0).toUpperCase() + role.slice(1);
}

export function roleToColor(role: string) {
    switch (role) {
        case "ROLE_SUPERADMIN":
            return "red";
        case "ROLE_ADMIN":
            return "orange";
        case "ROLE_USER":
            return "blue";
        default:
            return "gray";
    }
}