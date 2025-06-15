import {useTheme} from "next-themes";
import React, {useEffect, useState} from "react";
import {Button, Card, Divider, Select, Selection, SelectItem} from "@heroui/react";
import {themes} from "Frontend/theming/themes";
import {Theme} from "Frontend/theming/theme";
import ThemePreview from "Frontend/components/theming/ThemePreview";
import {toTitleCase} from "Frontend/util/utils";
import {UserPreferenceService} from "Frontend/util/user-preference-service";

export function ThemeSelector() {

    const {theme, setTheme} = useTheme();
    const [selectedTheme, setSelectedTheme] = useState(theme?.substring(0, theme?.lastIndexOf("-")));
    const [selectedMode, setSelectedMode] = useState<Selection>();

    useEffect(() => {
        if (!selectedMode)
            setSelectedMode(new Set([theme?.split('-').pop() ?? "dark"]));
    }, [theme]);

    useEffect(updateTheme, [selectedTheme, selectedMode]);

    function updateTheme() {
        if (selectedMode instanceof Set) {
            let theme = `${selectedTheme}-${selectedMode.values().next().value}`;
            setTheme(theme);
            UserPreferenceService.set("preferred-theme", theme).catch(console.error);
        }
    }

    return (
        <div className="flex flex-col items-center gap-8">
            <Select label="Theme mode" className="max-w-xs"
                    disallowEmptySelection
                    selectionMode={"single"}
                    defaultSelectedKeys={selectedMode}
                    onSelectionChange={setSelectedMode}
                    selectedKeys={selectedMode}>
                <SelectItem key="light">
                    Light
                </SelectItem>
                <SelectItem key="dark">
                    Dark
                </SelectItem>
            </Select>
            <div className="grid grid-flow-row grid-cols-8 gap-8">
                {
                    //min-w-[468px]
                    themes.map(((t: Theme) => (
                        <div className="size-[10vh] min-h-[50px] min-w-[50px]"
                             key={t.name}
                             onClick={() => setSelectedTheme(t.name)}>
                            <ThemePreview
                                theme={t}
                                isSelected={selectedTheme === t.name}/>
                        </div>
                    )))
                }
            </div>
            <p className="text-2xl font-semibold mt-8">Preview for theme
                "{toTitleCase(theme!.replaceAll("-", " "))}"
            </p>
            <Divider/>
            <div className="flex flex-row gap-8 items-baseline">
                <div className="flex flex-row gap-4">
                    <Button color="primary">Primary</Button>
                    <Button color="secondary">Secondary</Button>
                    <Button color="success">Success</Button>
                    <Button color="warning">Warning</Button>
                    <Button color="danger">Danger</Button>
                </div>
                <Card className="flex flex-row gap-4 p-4">
                    <Button color="primary">Primary</Button>
                    <Button color="secondary">Secondary</Button>
                    <Button color="success">Success</Button>
                    <Button color="warning">Warning</Button>
                    <Button color="danger">Danger</Button>
                </Card>
            </div>
        </div>
    )
}