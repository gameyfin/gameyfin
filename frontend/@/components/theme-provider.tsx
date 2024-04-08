import * as React from "react"
import {Provider as JotaiProvider} from "jotai"
import {ThemeProvider as NextThemesProvider} from "next-themes"
import {ThemeProviderProps} from "next-themes/dist/types"
import {TooltipProvider} from "Frontend/@/components/ui/tooltip";


export function ThemeProvider({children, ...props}: ThemeProviderProps) {
    return (
        <JotaiProvider>
            <NextThemesProvider {...props}>
                <TooltipProvider delayDuration={0}>{children}</TooltipProvider>
            </NextThemesProvider>
        </JotaiProvider>
    )
}
