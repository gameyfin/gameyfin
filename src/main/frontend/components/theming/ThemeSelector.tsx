import {useTheme} from "next-themes";
import React, {useLayoutEffect, useState} from "react";
import {Switch} from "@nextui-org/react";
import {Moon, SunDim} from "@phosphor-icons/react";
import {themes} from "Frontend/theming/themes";
import {Theme} from "Frontend/theming/theme";
import ThemePreview from "Frontend/components/theming/ThemePreview";

export function ThemeSelector() {

    const {theme, setTheme} = useTheme();
    const [isSelected, setIsSelected] = useState(theme ? theme.includes("light") : false);
    const [currentTheme, setCurrentTheme] = useState(theme?.substring(0, theme?.lastIndexOf("-")));

    useLayoutEffect(() => setTheme(`${currentTheme}-${mode()}`), [isSelected]);

    function mode(): "light" | "dark" {
        return isSelected ? "light" : "dark";
    }

    return (
        <div className="flex flex-col size-full items-center">
            <div className="w-full flex flex-row items-center justify-center gap-4 mb-16">
                <Switch
                    size="lg"
                    startContent={<SunDim size={32}/>}
                    endContent={<Moon size={32}/>}
                    isSelected={isSelected}
                    onValueChange={() => {
                        setIsSelected(!isSelected);
                    }}
                />

            </div>
            <div className="grid grid-flow-col auto-cols-auto auto-cols-max-4 gap-8">
                {
                    //min-w-[468px]
                    themes.map(((t: Theme) => (
                        <div
                            key={t.name}
                            onClick={() => {
                                setCurrentTheme(t.name);
                                setTheme(`${t.name}-${mode()}`);
                            }}>
                            <ThemePreview
                                theme={t}
                                mode={mode()}
                                isSelected={currentTheme === t.name}/>
                        </div>
                    )))
                }
            </div>
        </div>
    )
}