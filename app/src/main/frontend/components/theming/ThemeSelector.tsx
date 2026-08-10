import {useTheme} from "next-themes";
import React, {useEffect, useState} from "react";
import {Button, Card, Label, ListBox, Select, Separator} from "@heroui/react";
import {themes} from "Frontend/theming/themes";
import {Theme} from "Frontend/theming/theme";
import ThemePreview from "Frontend/components/theming/ThemePreview";
import {toTitleCase} from "Frontend/util/utils";
import {useUserPreferenceService} from "Frontend/util/user-preference-service";

export function ThemeSelector() {

    const {theme, setTheme} = useTheme();
    const [selectedTheme, setSelectedTheme] = useState(theme?.substring(0, theme?.lastIndexOf("-")));
    const [selectedMode, setSelectedMode] = useState<string>();
    const userPreferenceService = useUserPreferenceService();

    useEffect(() => {
        if (!selectedMode)
            setSelectedMode(theme?.split('-').pop() ?? "dark");
    }, [theme]);

    useEffect(updateTheme, [selectedTheme, selectedMode]);

    function updateTheme() {
        if (selectedMode) {
            let theme = `${selectedTheme}-${selectedMode}`;
            setTheme(theme);
            userPreferenceService.set("preferred-theme", theme).catch(console.error);
        }
    }

    return (
        <div className="flex flex-col items-center gap-8">
            <Select className="max-w-xs"
                    value={selectedMode}
                    onChange={(value) => setSelectedMode(value as string)}>
                <Label>Theme mode</Label>
                <Select.Trigger>
                    <Select.Value/>
                    <Select.Indicator/>
                </Select.Trigger>
                <Select.Popover>
                    <ListBox>
                        <ListBox.Item key="light" id="light" textValue="Light">
                            Light
                            <ListBox.ItemIndicator/>
                        </ListBox.Item>
                        <ListBox.Item key="dark" id="dark" textValue="Dark">
                            Dark
                            <ListBox.ItemIndicator/>
                        </ListBox.Item>
                    </ListBox>
                </Select.Popover>
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
            <Separator/>
            <div className="flex flex-row gap-8 items-baseline">
                <div className="flex flex-row gap-4">
                    <Button variant="primary">Primary</Button>
                    <Button variant="secondary">Secondary</Button>
                    <Button variant="danger">Danger</Button>
                </div>
                <Card className="flex flex-row gap-4 p-4">
                    <Button variant="primary">Primary</Button>
                    <Button variant="secondary">Secondary</Button>
                    <Button variant="danger">Danger</Button>
                </Card>
            </div>
        </div>
    )
}