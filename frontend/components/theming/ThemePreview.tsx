import {Theme} from "Frontend/theming/theme";
import {Card, Tooltip} from "@nextui-org/react";
import GameyfinLogo from "Frontend/components/theming/GameyfinLogo";

export default function ThemePreview({theme, mode, isSelected}: {
    theme: Theme,
    mode: "light" | "dark",
    isSelected?: boolean
}) {
    return (
        <Tooltip content={<p className="capitalize">{theme.name?.replace("-", " ")}</p>} placement="bottom">
            <Card
                shadow="none"
                className={`${theme.name}-${mode} flex-row justify-center p-6 border-2 ${isSelected ? "border-focus" : "border-foreground-200 hover:border-focus"}`}
            >
                <GameyfinLogo className="w-1/2 fill-primary"/>
            </Card>
        </Tooltip>
    );
}

/*
<svg width="228" height="120" viewBox="0 0 228 120" fill="none" xmlns="http://www.w3.org/2000/svg">
    <path id="background" d="M0 0H228V120H0V0Z" fill={theme.cssVars[resolvedTheme].background}/>
    <rect id="background-secondary" x="29" y="54" width="144" height="53" rx="2" fill="#30363D"/>
    <rect x="184" y="54" width="32" height="36" rx="2" fill="#30363D"/>
    <rect opacity="0.3" x="29" y="59" width="144" height="12" fill="#2EA043"/>
    <path opacity="0.6" d="M0 0H228V23H0V0Z" fill="#484F58"/>
    <rect x="13" y="9" width="32" height="6" rx="3" fill="#8B949E"/>
    <rect x="29" y="36" width="48" height="6" rx="3" fill="#6E7681"/>
    <rect x="34" y="62" width="64" height="6" rx="3" fill="#3FB950"/>
    <rect x="210" y="36" width="6" height="6" rx="1" fill="#DA3633"/>
    <rect x="202" y="36" width="6" height="6" rx="1" fill="#3FB950"/>
    <rect x="53" y="9" width="32" height="6" rx="3" fill="#8B949E"/>
    <rect x="93" y="9" width="32" height="6" rx="3" fill="#8B949E"/>
</svg>
*/