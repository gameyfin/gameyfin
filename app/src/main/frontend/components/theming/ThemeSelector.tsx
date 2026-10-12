import {useTheme} from "next-themes";
import React, {useEffect, useMemo, useRef, useState} from "react";
import {Button, Card, ColorSwatchPicker, Label, ListBox, Select, Separator} from "@heroui/react";
import {themes, themeNames, applyAccentTheme} from "Frontend/theming/themes";
import {Theme} from "Frontend/theming/theme";
import ThemePreview from "Frontend/components/theming/ThemePreview";
import {toTitleCase} from "Frontend/util/utils";
import {useUserPreferenceService} from "Frontend/util/user-preference-service";
import type {Color} from "@heroui/react";

export function ThemeSelector() {

    const {theme: mode, setTheme: setMode} = useTheme();
    // Start from the accent theme that is currently applied, not themes[0], so opening this view
    // doesn't reset the user's theme.
    const [selectedTheme, setSelectedTheme] = useState(
        () => themeNames().find((n) => document.documentElement.classList.contains(n)) ?? themes[0].name!
    );
    // Don't persist anything until the stored preference has been loaded, otherwise the initial
    // state would overwrite it.
    const preferenceLoaded = useRef(false);
    const userPreferenceService = useUserPreferenceService();

    const selectedThemeAccent = useMemo(
        () => themes.find((t) => t.name === selectedTheme)?.colors.accent,
        [selectedTheme]
    );

    useEffect(() => {
        userPreferenceService.get("preferred-theme").then((syncedTheme) => {
            if (syncedTheme !== undefined) {
                const separatorIndex = syncedTheme.lastIndexOf("-");
                setSelectedTheme(syncedTheme.substring(0, separatorIndex));
            }
        }).catch(console.error).finally(() => {
            preferenceLoaded.current = true;
        });
    }, []);

    useEffect(() => {
        applyAccentTheme(selectedTheme);
        updateTheme();
    }, [selectedTheme, mode]);

    function updateTheme() {
        if (mode && preferenceLoaded.current) {
            let theme = `${selectedTheme}-${mode}`;
            userPreferenceService.set("preferred-theme", theme).catch(console.error);
        }
    }

    function onColorChange(color: Color) {
        const hex = color.toString("hex").toLowerCase();
        const match = themes.find((t) => t.colors.accent.toLowerCase() === hex);
        if (match) setSelectedTheme(match.name!);
    }

    return (
        <div className="flex flex-col items-center gap-8">
            <Select className="max-w-xs"
                    value={mode}
                    onChange={(value) => setMode(value as string)}>
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
            <ColorSwatchPicker
                className="grid grid-flow-row grid-cols-8 gap-8"
                variant="square"
                value={selectedThemeAccent}
                onChange={onColorChange}
            >
                {
                    themes.map(((t: Theme) => (
                        <ThemePreview key={t.name} theme={t}/>
                    )))
                }
            </ColorSwatchPicker>
            <p className="text-2xl font-semibold mt-8">Preview for theme
                "{toTitleCase(selectedTheme.replaceAll("-", " "))}"
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