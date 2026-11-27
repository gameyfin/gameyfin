import {Label, ListBox, Select} from "@heroui/react";
import CollectionVisibility from "Frontend/generated/org/gameyfin/app/collections/entities/CollectionVisibility";

const visibilityOptions = [
    {id: CollectionVisibility.PRIVATE, label: "Private"},
    {id: CollectionVisibility.MEMBERS_ONLY, label: "Members only"},
    {id: CollectionVisibility.PUBLIC, label: "Public"}
];

export default function CollectionVisibilitySelect({value, onChange}: {
    value?: CollectionVisibility;
    onChange: (value: CollectionVisibility) => void;
}) {
    return <Select className="w-full" value={value} onChange={key => {
        const option = visibilityOptions.find(item => item.id === key);
        if (option) onChange(option.id);
    }}>
        <Label>Visibility</Label>
        <Select.Trigger><Select.Value/><Select.Indicator/></Select.Trigger>
        <Select.Popover>
            <ListBox items={visibilityOptions}>
                {option => <ListBox.Item id={option.id} textValue={option.label}>
                    {option.label}<ListBox.ItemIndicator/>
                </ListBox.Item>}
            </ListBox>
        </Select.Popover>
    </Select>;
}
