import React, {useEffect} from "react";
import {Autocomplete, EmptyState, Key, Label, ListBox, SearchField, Tag, TagGroup, useFilter} from "@heroui/react";
import {useField} from "formik";

type AutocompleteInputProps = {
    label?: string;
    placeholder?: string;
    options: string[];
    name: string;
    defaultSelected?: string[];
};

export default function AutocompleteInput({
                                              options,
                                              label,
                                              placeholder = "Select...",
                                              defaultSelected = [],
                                              name
                                          }: AutocompleteInputProps) {
    const [field, meta, helpers] = useField<string[]>(name);
    const {contains} = useFilter({sensitivity: "base"});
    const selectedValues: string[] = field.value || [];

    // Initialize field value if undefined or empty
    useEffect(() => {
        if (!field.value) {
            helpers.setValue(defaultSelected.length > 0 ? defaultSelected : []);
        } else if (field.value.length === 0 && defaultSelected.length > 0) {
            helpers.setValue(defaultSelected);
        }
    }, [defaultSelected, field.value, helpers]);

    const updateValue = (keys: Key[]) => {
        helpers.setValue(keys.map(String));
        helpers.setTouched(true, false);
    };

    return (
        <div className="flex flex-col flex-1 gap-2">
            <Autocomplete
                fullWidth
                placeholder={placeholder}
                selectionMode="multiple"
                value={selectedValues}
                onChange={(keys: Key | Key[] | null) => updateValue(Array.isArray(keys) ? keys : keys == null ? [] : [keys])}
                isInvalid={meta.touched && !!meta.error}
            >
                {label && (
                    <div className="flex flex-row justify-between">
                        <Label>{label}</Label>
                        <small>{selectedValues.length} {selectedValues.length === 1 ? "element" : "elements"} selected</small>
                    </div>
                )}
                <Autocomplete.Trigger>
                    <Autocomplete.Value>
                        {({defaultChildren, isPlaceholder, state}: any) => {
                            if (isPlaceholder || state.selectedItems.length === 0) {
                                return defaultChildren;
                            }
                            return (
                                <TagGroup size="sm"
                                          onRemove={(keys: Set<Key>) => updateValue(selectedValues.filter(v => !keys.has(v)))}>
                                    <TagGroup.List>
                                        {state.selectedItems.map((item: any) => (
                                            <Tag key={item.key} id={item.key}>{String(item.key)}</Tag>
                                        ))}
                                    </TagGroup.List>
                                </TagGroup>
                            );
                        }}
                    </Autocomplete.Value>
                    <Autocomplete.Indicator/>
                </Autocomplete.Trigger>
                <Autocomplete.Popover>
                    <Autocomplete.Filter filter={contains}>
                        <SearchField autoFocus aria-label="Search options" name="search" variant="secondary">
                            <SearchField.Group>
                                <SearchField.SearchIcon/>
                                <SearchField.Input placeholder="Search..."/>
                                <SearchField.ClearButton/>
                            </SearchField.Group>
                        </SearchField>
                        <ListBox renderEmptyState={() => <EmptyState>No results found</EmptyState>}>
                            {options.map((option) => (
                                <ListBox.Item key={option} id={option} textValue={option}>
                                    {option}
                                    <ListBox.ItemIndicator/>
                                </ListBox.Item>
                            ))}
                        </ListBox>
                    </Autocomplete.Filter>
                </Autocomplete.Popover>
            </Autocomplete>

            <div className="min-h-6 text-danger">
                {meta.touched && meta.error && meta.error.trim().length > 0 && meta.error}
            </div>
        </div>
    );
}
