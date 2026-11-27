import {Button, Input, Label, ListBox, Select, TextField} from "@heroui/react";
import {TrashIcon} from "@phosphor-icons/react";
import {
    type ActionProps,
    isOptionGroupArray,
    isRuleGroup,
    QueryBuilder,
    type RuleGroupType,
    type ValueEditorProps,
    type ValueSelectorProps
} from "react-querybuilder";
import {useSnapshot} from "valtio/react";
import type SmartCollectionEditor from "Frontend/generated/org/gameyfin/app/collections/dto/SmartCollectionEditor";
import SmartField from "Frontend/generated/org/gameyfin/app/collections/dto/SmartField";
import SmartMatch from "Frontend/generated/org/gameyfin/app/collections/dto/SmartMatch";
import SmartOperator from "Frontend/generated/org/gameyfin/app/collections/dto/SmartOperator";
import type SmartRule from "Frontend/generated/org/gameyfin/app/collections/dto/SmartRule";
import type SmartRuleOption from "Frontend/generated/org/gameyfin/app/collections/dto/SmartRuleOption";
import {libraryState} from "Frontend/state/LibraryState";

const equals = {name: SmartOperator.EQUALS, label: "Equals"};
const textOperators = [
    equals,
    {name: SmartOperator.CONTAINS, label: "Contains"},
    {name: SmartOperator.STARTS_WITH, label: "Starts with"}
];
const fieldLabels = {
    [SmartField.TITLE]: "Title",
    [SmartField.DEVELOPER]: "Developer",
    [SmartField.PUBLISHER]: "Publisher",
    [SmartField.GENRE]: "Genre",
    [SmartField.THEME]: "Theme",
    [SmartField.KEYWORD]: "Keyword",
    [SmartField.PLATFORM]: "Platform",
    [SmartField.LIBRARY]: "Library"
};

type FieldOptions = Partial<Record<SmartField, SmartRuleOption[]>>;

function RuleSelect({label, value, options, onChange, disabled, placeholder}: {
    label: string;
    value?: string;
    options: SmartRuleOption[];
    onChange: (value: string) => void;
    disabled?: boolean;
    placeholder?: string;
}) {
    return <Select className="min-w-32 w-full sm:w-auto" aria-label={label}
                   value={value || null} onChange={key => key != null && onChange(String(key))}
                   isDisabled={disabled} placeholder={placeholder}>
        <Select.Trigger><Select.Value/><Select.Indicator/></Select.Trigger>
        <Select.Popover>
            <ListBox items={options}>
                {option => <ListBox.Item id={option.value} textValue={option.label}>
                    {option.label}<ListBox.ItemIndicator/>
                </ListBox.Item>}
            </ListBox>
        </Select.Popover>
    </Select>;
}

function QuerySelector({value, options, handleOnChange, disabled, title}: ValueSelectorProps) {
    const entries = isOptionGroupArray(options) ? options.flatMap(group => group.options) : options;
    return <RuleSelect label={title ?? "Rule option"} value={value} options={entries}
                       onChange={handleOnChange} disabled={disabled}/>;
}

function QueryValue({field, type, value, values, handleOnChange, disabled}: ValueEditorProps) {
    const label = fieldLabels[field as SmartField];
    if (type === "select") {
        const options: SmartRuleOption[] = values ?? [];
        const selected = value && !options.some(option => option.value === value)
            ? [...options, {value, label: "Unavailable library"}]
            : options;
        return <RuleSelect label={`${label} value`} value={value} options={selected} onChange={handleOnChange}
                           disabled={disabled} placeholder={`Choose ${label.toLowerCase()}`}/>;
    }
    return <TextField className="min-w-32 grow" value={value ?? ""} onChange={handleOnChange} isDisabled={disabled}>
        <Label className="sr-only">{label} value</Label>
        <Input maxLength={255} placeholder={`Enter ${label.toLowerCase()}`}/>
    </TextField>;
}

function AddAction({label, handleOnClick, disabled}: ActionProps) {
    return <Button type="button" variant="secondary" size="sm" onPress={() => handleOnClick()} isDisabled={disabled}>
        {label}
    </Button>;
}

function RemoveAction({title, handleOnClick, disabled}: ActionProps) {
    return <Button type="button" isIconOnly variant="ghost" size="sm" aria-label={title}
                   onPress={() => handleOnClick()} isDisabled={disabled}>
        <TrashIcon/>
    </Button>;
}

const controls = {
    combinatorSelector: QuerySelector,
    fieldSelector: QuerySelector,
    operatorSelector: QuerySelector,
    valueEditor: QueryValue,
    addRuleAction: AddAction,
    addGroupAction: AddAction,
    removeRuleAction: RemoveAction,
    removeGroupAction: RemoveAction
};

export function toRuleGroup(rule?: SmartRule): RuleGroupType {
    if (!rule?.match) return {
        combinator: SmartMatch.ALL,
        rules: [{field: SmartField.TITLE, operator: SmartOperator.CONTAINS, value: ""}]
    };
    return {
        combinator: rule.match,
        rules: (rule.rules ?? []).map(child => child.match ? toRuleGroup(child) : ({
            field: child.field ?? SmartField.TITLE,
            operator: child.operator ?? SmartOperator.CONTAINS,
            value: child.value ?? ""
        }))
    };
}

export function toSmartRule(group: RuleGroupType): SmartRule {
    return {
        match: group.combinator as SmartMatch,
        rules: group.rules.map(rule => isRuleGroup(rule) ? toSmartRule(rule) : ({
            field: rule.field as SmartField,
            operator: rule.operator as SmartOperator,
            value: String(rule.value ?? "").trim()
        }))
    };
}

export function ruleError(root: SmartRule): string | undefined {
    let count = 0;

    function check(rule: SmartRule, depth: number): string | undefined {
        if (depth > 5 || ++count > 50) return "Use at most 50 rules and groups, up to five levels deep.";
        if (!rule.match) return rule.value ? undefined : "Enter a value for each rule.";
        if (!rule.rules?.length) return "Add at least one rule to each group.";
        for (const child of rule.rules) {
            const error = check(child, depth + 1);
            if (error) return error;
        }
        return undefined;
    }

    return check(root, 1);
}

export default function SmartCollectionRulesEditor({query, onChange, editor, disabled}: {
    query: RuleGroupType;
    onChange: (query: RuleGroupType) => void;
    editor: SmartCollectionEditor;
    disabled: boolean;
}) {
    const libraries = useSnapshot(libraryState).libraries;
    const options: FieldOptions = {
        [SmartField.GENRE]: editor.genres,
        [SmartField.THEME]: editor.themes,
        [SmartField.PLATFORM]: editor.platforms,
        [SmartField.LIBRARY]: [...libraries]
            .sort((a, b) => a.name.localeCompare(b.name))
            .map(library => ({value: String(library.id), label: library.name}))
    };
    const fields = Object.entries(fieldLabels).map(([name, label]) => ({
        name,
        value: name,
        label,
        operators: name in options ? [equals] : textOperators,
        values: options[name as SmartField]?.map(({value, label}) => ({name: value, value, label})),
        valueEditorType: name in options ? "select" as const : "text" as const
    }));
    return <QueryBuilder fields={fields} query={query} onQueryChange={onChange} disabled={disabled}
                         addRuleToNewGroups
                         combinators={[
                             {name: SmartMatch.ALL, label: "Match all"},
                             {name: SmartMatch.ANY, label: "Match any"}
                         ]}
                         controlElements={controls}
                         controlClassnames={{
                             ruleGroup: "min-w-0 rounded-xl bg-surface-secondary p-3",
                             header: "flex flex-wrap items-center gap-2",
                             body: "mt-3 flex flex-col gap-2 [&>.ruleGroup]:border-s-2 [&>.ruleGroup]:border-separator sm:[&>.ruleGroup]:ms-3",
                             rule: "flex flex-wrap items-center gap-2 rounded-lg bg-surface p-2"
                         }}
                         translations={{
                             addRule: {label: "Add rule"},
                             addGroup: {label: "Add group"},
                             fields: {title: "Rule field"},
                             operators: {title: "Rule operator"},
                             combinators: {title: "Group matching"},
                             removeRule: {title: "Remove rule"},
                             removeGroup: {title: "Remove group"}
                         }}/>;
}
