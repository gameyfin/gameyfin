import React, {Key, useEffect, useState} from "react";
import {Autocomplete, AutocompleteItem, Chip} from "@heroui/react";
import {FieldArray, useField} from "formik";

type ArrayInputAutocompleteProps = {
    label?: string;
    placeholder?: string;
    options: string[];
    name: string;
    defaultSelected?: string[];
};

export default function ArrayInputAutocomplete({
                                                   options,
                                                   label,
                                                   placeholder = "Search...",
                                                   defaultSelected = [],
                                                   ...props
                                               }: ArrayInputAutocompleteProps) {
    const [field, meta, helpers] = useField(props);
    const [search, setSearch] = useState("");

    // Initialize field value if undefined or empty
    useEffect(() => {
        if (!field.value) {
            helpers.setValue(defaultSelected.length > 0 ? defaultSelected : []);
        } else if (field.value.length === 0 && defaultSelected.length > 0) {
            helpers.setValue(defaultSelected);
        }
    }, [defaultSelected, field.value, helpers]);

    return (
        <FieldArray name={field.name}
                    render={arrayHelpers => {
                        const selectedValues = field.value || [];
                        const filteredOptions = options.filter(
                            (option) =>
                                option.toLowerCase().includes(search.toLowerCase()) &&
                                !selectedValues.find((selected: string) => selected === option),
                        );

                        const handleSelect = (item: string) => {
                            if (!selectedValues.find((selected: string) => selected === item)) {
                                arrayHelpers.push(item);
                            }
                        };

                        const handleRemove = (index: number) => {
                            arrayHelpers.remove(index);
                        };

                        return (
                            <div className="flex flex-col flex-1 gap-2">
                                {label && (
                                    <div className="flex flex-row justify-between">
                                        <p>{label}</p>
                                        <small>{selectedValues.length} {selectedValues.length === 1 ? "element" : "elements"} selected</small>
                                    </div>
                                )}

                                <Autocomplete
                                    {...props}
                                    aria-labelledby="search"
                                    shouldCloseOnBlur={false}
                                    placeholder={placeholder}
                                    inputValue={search}
                                    onInputChange={(value) => setSearch(value)}
                                    onSelectionChange={(value: Key | null) => {
                                        const item = options.find((option) => option === value);
                                        if (item) handleSelect(item);
                                        setSearch("");
                                    }}
                                >
                                    {filteredOptions.map((option) => (
                                        <AutocompleteItem key={option} data-selected="true">
                                            {option}
                                        </AutocompleteItem>
                                    ))}
                                </Autocomplete>

                                <div className="flex flex-wrap gap-2">
                                    {selectedValues.map((item: string, index: number) => (
                                        <Chip key={index} variant="flat"
                                              onClose={() => handleRemove(index)}>
                                            {item}
                                        </Chip>
                                    ))}
                                </div>

                                <div className="min-h-6 text-danger">
                                    {meta.touched && meta.error && meta.error.trim().length > 0 && (
                                        meta.error
                                    )}
                                </div>
                            </div>
                        );
                    }}
        />
    );
}
