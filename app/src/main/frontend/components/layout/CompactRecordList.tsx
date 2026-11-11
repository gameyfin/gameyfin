import {SortAscendingIcon, SortDescendingIcon} from "@phosphor-icons/react";
import {ReactNode} from "react";
import {Button, Card, Label, ListBox, Select, SortDescriptor, Tooltip} from "@heroui/react";

interface Props<T> {
    label: string;
    items: readonly T[];
    itemKey: (item: T) => string | number;
    fields: (item: T) => {label: string; content: ReactNode}[];
    empty: ReactNode;
    sort?: {
        value: SortDescriptor;
        onChange: (value: SortDescriptor) => void;
        options: {id: string; label: string}[];
    };
}

/** Feature-owned fields and handlers are shared with the desktop table. */
export default function CompactRecordList<T>({label, items, itemKey, fields, empty, sort}: Props<T>) {
    return <section aria-label={label} className="min-w-0 space-y-3">
        {sort && <div className="flex flex-wrap items-end gap-2">
            <Select className="min-w-0 flex-1" value={String(sort.value.column)}
                    onChange={column => column != null && sort.onChange({...sort.value, column})}>
                <Label>Sort by</Label>
                <Select.Trigger><Select.Value/><Select.Indicator/></Select.Trigger>
                <Select.Popover><ListBox items={sort.options}>
                    {option => <ListBox.Item id={option.id} textValue={option.label}>
                        {option.label}<ListBox.ItemIndicator/>
                    </ListBox.Item>}
                </ListBox></Select.Popover>
            </Select>
            <Tooltip>
                <Button isIconOnly variant="secondary" className="min-h-11 min-w-11 shrink-0"
                        aria-label={sort.value.direction === "ascending" ? "Ascending" : "Descending"}
                        onPress={() => sort.onChange({...sort.value,
                    direction: sort.value.direction === "ascending" ? "descending" : "ascending"})}>
                    {sort.value.direction === "ascending" ? <SortAscendingIcon aria-hidden="true"/> : <SortDescendingIcon aria-hidden="true"/>}
                </Button>
                <Tooltip.Content>{sort.value.direction === "ascending" ? "Sort descending" : "Sort ascending"}</Tooltip.Content>
            </Tooltip>
        </div>}
        {items.length === 0 ? empty : <ul className="grid min-w-0 gap-3 sm:grid-cols-2">
            {items.map(item => {
                const [title, ...details] = fields(item);
                const actions = details.pop();
                return <li key={itemKey(item)} className="min-w-0">
                    <Card className="h-full min-w-0 wrap-anywhere">
                        <Card.Header><div className="font-semibold">{title.content}</div></Card.Header>
                        <Card.Content>
                            <dl className="space-y-2 text-sm">
                                {details.map(field => <div key={field.label}>
                                    <dt className="text-muted">{field.label}</dt><dd>{field.content}</dd>
                                </div>)}
                            </dl>
                        </Card.Content>
                        {actions && <Card.Footer className="compact-record-actions flex-wrap mt-auto">
                            {actions.content}
                        </Card.Footer>}
                    </Card>
                </li>;
            })}
        </ul>}
    </section>;
}
