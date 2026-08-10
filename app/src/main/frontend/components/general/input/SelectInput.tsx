import {useField} from "formik";
import {Description, FieldError, Label, ListBox, Select, SelectProps} from "@heroui/react";
import InfoPopup from "Frontend/components/administration/InfoPopup";
import ResetToDefaultButton from "Frontend/components/administration/ResetToDefaultButton";

interface SelectInputProps extends Omit<SelectProps<object>, "name" | "children" | "value" | "onChange"> {
    label: string;
    name: string;
    values: string[];
    description?: string;
    resetValue?: unknown;
}

export default function SelectInput({label, values, description, resetValue, ...props}: SelectInputProps) {
    const [field, meta, helpers] = useField(props.name);

    return (
        <div className="min-h-20 grow">
            <Select
                fullWidth={true}
                {...props}
                value={field.value}
                onChange={(value) => helpers.setValue(value)}
                isInvalid={!!meta.error}
            >
                <Label>{label}</Label>
                <Select.Trigger>
                    <Select.Value/>
                    <Select.Indicator/>
                </Select.Trigger>
                {(description || resetValue !== undefined) && (
                    <span className="flex items-center">
                        {description && <InfoPopup content={description}/>}
                        {resetValue !== undefined &&
                            <ResetToDefaultButton fieldName={field.name} defaultValue={resetValue}/>}
                    </span>
                )}
                <Select.Popover>
                    <ListBox>
                        {values.map((v) => (
                            <ListBox.Item key={v} id={v} textValue={v}>
                                {v}
                                <ListBox.ItemIndicator/>
                            </ListBox.Item>
                        ))}
                    </ListBox>
                </Select.Popover>
                <FieldError>{meta.initialError || meta.error}</FieldError>
            </Select>
        </div>
    );
}
