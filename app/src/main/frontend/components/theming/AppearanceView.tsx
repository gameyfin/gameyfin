import React, {useEffect} from "react";
import {Checkbox, Separator} from "@heroui/react";
import {useSnapshot} from "valtio/react";
import {ThemeSelector} from "Frontend/components/theming/ThemeSelector";
import {layoutState} from "Frontend/state/LayoutState";
import {useUserPreferenceService} from "Frontend/util/user-preference-service";

export default function AppearanceView() {
    const {stickyHeader} = useSnapshot(layoutState);
    const userPreferenceService = useUserPreferenceService();

    useEffect(() => {
        userPreferenceService.get("sticky-header").then((value) => {
            if (value !== undefined) layoutState.stickyHeader = value === "true";
        }).catch(console.error);
    }, []);

    function onStickyHeaderChange(value: boolean) {
        layoutState.stickyHeader = value;
        userPreferenceService.set("sticky-header", String(value)).catch(console.error);
    }

    return (
        <div className="flex flex-col items-center gap-8">
            <div className="w-full flex flex-col items-start gap-4">
                <h2 className="text-2xl font-semibold">Layout</h2>
                <Checkbox isSelected={stickyHeader} onChange={onStickyHeaderChange}>
                    <Checkbox.Content>
                        <div className="flex flex-row items-center gap-2">
                            <Checkbox.Control>
                                <Checkbox.Indicator/>
                            </Checkbox.Control>
                            <span>Keep navigation bar visible while scrolling</span>
                        </div>
                    </Checkbox.Content>
                </Checkbox>
            </div>
            <Separator className="w-full"/>
            <div className="w-full flex flex-col items-center gap-4">
                <h2 className="text-2xl font-semibold self-start">Theme</h2>
                <ThemeSelector/>
            </div>
        </div>
    );
}
