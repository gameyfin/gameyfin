import React, {useEffect, useLayoutEffect, useRef, useState} from "react";
import {Autocomplete, Chip, EmptyState, Key, Label, ListBox, SearchField, Tag, TagGroup, useFilter} from "@heroui/react";
import {useField} from "formik";

export type AutocompleteOption = string | { id: string; label: string };

type AutocompleteInputProps = {
    label?: string;
    placeholder?: string;
    options: AutocompleteOption[];
    name: string;
    defaultSelected?: string[];
    selectionMode?: "single" | "multiple";
    className?: string;
    triggerPrefix?: React.ReactNode;
    showCount?: boolean;
    showError?: boolean;
};

function SelectedTags({ids, labelOf, onRemove}: {
    ids: Key[];
    labelOf: (id: Key) => string;
    onRemove: (keys: Set<Key>) => void;
}) {
    const containerRef = useRef<HTMLDivElement>(null);
    const measureListRef = useRef<HTMLDivElement>(null);
    const measureChipRef = useRef<HTMLDivElement>(null);
    const [visibleCount, setVisibleCount] = useState(ids.length);

    // Determine how many tags fit into the available width, leaving room for the "+N" chip if some have to be hidden
    const idsCountRef = useRef(ids.length);
    idsCountRef.current = ids.length;

    const recalculate = () => {
        const container = containerRef.current;
        const list = measureListRef.current?.querySelector<HTMLElement>('[data-slot="tag-group-list"]');
        if (!container || !list) return;

        const widths = Array.from(list.children).map((child) => (child as HTMLElement).getBoundingClientRect().width);
        // The tag collection renders asynchronously; wait until all tags are present before measuring
        if (widths.length !== idsCountRef.current) return;
        const gap = parseFloat(getComputedStyle(list).columnGap) || 4;
        const chipWidth = (measureChipRef.current?.getBoundingClientRect().width ?? 40) + gap;
        // Subtract a pixel as safety margin against sub-pixel rounding
        const available = container.clientWidth - 1;

        const usedBy = (count: number) => widths.slice(0, count).reduce((sum, w) => sum + w + gap, 0);
        let count = widths.length;
        if (usedBy(count) - gap > available) {
            count = widths.length - 1;
            while (count > 0 && usedBy(count) + chipWidth > available) count--;
        }
        setVisibleCount(count);
    };

    const idsKey = ids.map(String).join("\u0000");
    useLayoutEffect(recalculate, [idsKey]);
    useLayoutEffect(() => {
        const container = containerRef.current;
        if (!container) return;
        const resizeObserver = new ResizeObserver(recalculate);
        resizeObserver.observe(container);
        const mutationObserver = new MutationObserver(recalculate);
        if (measureListRef.current) {
            mutationObserver.observe(measureListRef.current, {childList: true, subtree: true});
        }
        return () => {
            resizeObserver.disconnect();
            mutationObserver.disconnect();
        };
    }, []);

    const visible = ids.slice(0, visibleCount);
    const hiddenCount = ids.length - visibleCount;

    // w-0 + min-w-full: fill the available width without letting the tags widen any ancestor (e.g. flex/grid items)
    return (
        <div ref={containerRef} className="relative flex flex-row flex-nowrap items-center gap-1 w-0 min-w-full overflow-hidden whitespace-nowrap"
             title={ids.map(labelOf).join(", ")}>
            {visible.length > 0 && (
                <TagGroup size="sm" className="shrink-0" onRemove={onRemove}>
                    <TagGroup.List className="flex-nowrap">
                        {visible.map((id) => <Tag key={id} id={id} className="shrink-0 whitespace-nowrap">{labelOf(id)}</Tag>)}
                    </TagGroup.List>
                </TagGroup>
            )}
            {hiddenCount > 0 && <Chip size="sm" variant="secondary" className="shrink-0 whitespace-nowrap">+{hiddenCount}</Chip>}

            {/* Invisible copy used only to measure the natural width of the tags */}
            <div aria-hidden inert className="absolute left-0 top-0 invisible pointer-events-none flex flex-row gap-1 w-max">
                <div ref={measureListRef}>
                    <TagGroup size="sm" aria-label="Measure" onRemove={() => {
                    }}>
                        <TagGroup.List className="flex-nowrap">
                            {ids.map((id) => <Tag key={id} id={id} className="shrink-0 whitespace-nowrap">{labelOf(id)}</Tag>)}
                        </TagGroup.List>
                    </TagGroup>
                </div>
                <div ref={measureChipRef}><Chip size="sm" variant="secondary" className="whitespace-nowrap">+{ids.length >= 10 ? 99 : 9}</Chip></div>
            </div>
        </div>
    );
}

export default function AutocompleteInput({
                                              options,
                                              label,
                                              placeholder = "Select...",
                                              defaultSelected = [],
                                              name,
                                              selectionMode = "multiple",
                                              className = "flex flex-col flex-1 gap-2",
                                              triggerPrefix,
                                              showCount = true,
                                              showError = true
                                          }: AutocompleteInputProps) {
    const [field, meta, helpers] = useField<string[] | string>(name);
    const {contains} = useFilter({sensitivity: "base"});
    const multiple = selectionMode === "multiple";

    const items = options.map((o) => typeof o === "string" ? {id: o, label: o} : o);
    const labelOf = (id: Key) => items.find((item) => item.id === String(id))?.label ?? String(id);
    const selectedValues: string[] = multiple ? ((field.value as string[]) || []) : [];

    // Initialize field value if undefined or empty
    useEffect(() => {
        if (!multiple) return;
        const value = field.value as string[] | undefined;
        if (!value) {
            helpers.setValue(defaultSelected.length > 0 ? defaultSelected : []);
        } else if (value.length === 0 && defaultSelected.length > 0) {
            helpers.setValue(defaultSelected);
        }
    }, [multiple, defaultSelected, field.value, helpers]);

    const updateValue = (keys: Key[]) => {
        helpers.setValue(keys.map(String));
        helpers.setTouched(true, false);
    };

    return (
        <div className={className}>
            <Autocomplete
                fullWidth
                placeholder={placeholder}
                selectionMode={selectionMode}
                value={multiple ? selectedValues : (field.value as string)}
                onChange={(keys: Key | Key[] | null) => {
                    if (multiple) {
                        updateValue(Array.isArray(keys) ? keys : keys == null ? [] : [keys]);
                    } else if (keys != null) {
                        helpers.setValue(String(Array.isArray(keys) ? keys[0] : keys));
                        helpers.setTouched(true, false);
                    }
                }}
                isInvalid={meta.touched && !!meta.error}
            >
                {label && (
                    <div className="flex flex-row justify-between">
                        <Label>{label}</Label>
                        {multiple && showCount &&
                            <small>{selectedValues.length} {selectedValues.length === 1 ? "element" : "elements"} selected</small>}
                    </div>
                )}
                <Autocomplete.Trigger className="flex items-center gap-2 min-w-0 overflow-hidden">
                    {triggerPrefix}
                    <Autocomplete.Value className="flex-1 min-w-0 overflow-hidden">
                        {({defaultChildren, isPlaceholder, state}: any) => {
                            if (!multiple || isPlaceholder || state.selectedItems.length === 0) {
                                return defaultChildren;
                            }
                            return (
                                <SelectedTags
                                    ids={state.selectedItems.map((item: any) => item.key)}
                                    labelOf={labelOf}
                                    onRemove={(keys) => updateValue(selectedValues.filter(v => !keys.has(v)))}
                                />
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
                            {items.length === 0 && (
                                // An empty collection would prevent the popover from opening
                                <ListBox.Item id="__no-items__" textValue="No items" isDisabled>
                                    No items
                                </ListBox.Item>
                            )}
                            {items.map((item) => (
                                <ListBox.Item key={item.id} id={item.id} textValue={item.label}>
                                    {item.label}
                                    <ListBox.ItemIndicator/>
                                </ListBox.Item>
                            ))}
                        </ListBox>
                    </Autocomplete.Filter>
                </Autocomplete.Popover>
            </Autocomplete>

            {showError && (
                <div className="min-h-6 text-danger">
                    {meta.touched && meta.error && meta.error.trim().length > 0 && meta.error}
                </div>
            )}
        </div>
    );
}
