import {useField} from "formik";
import {Select, SelectItem, SelectProps} from "@heroui/react";
import InfoPopup from "Frontend/components/administration/InfoPopup";
import ResetToDefaultButton from "Frontend/components/administration/ResetToDefaultButton";

interface SelectInputProps extends Omit<SelectProps, "name" | "children"> {
    label: string;
    name: string;
    values: string[];
    description?: string;
    resetValue?: unknown;
}

export default function SelectInput({label, values, description, resetValue, ...props}: SelectInputProps) {
    const [field, meta] = useField(props.name);

    const items = values.map((v: string) => ({key: v, label: v}));

    return (
        <div className="min-h-20 grow">
            <Select
                fullWidth={true}
                {...field}
                {...props}
                label={label}
                items={items}
                selectedKeys={[field.value]}
                endContent={
                    (description || resetValue !== undefined) ? (
                        <span className="flex items-center">
                            {description && <InfoPopup content={description}/>}
                            {resetValue !== undefined &&
                                <ResetToDefaultButton fieldName={field.name} defaultValue={resetValue}/>}
                        </span>
                    ) : undefined
                }
                isInvalid={!!meta.error}
                errorMessage={meta.initialError || meta.error}
                disallowEmptySelection
            >
                {(item: { key: string, label: string }) => <SelectItem>{item.label}</SelectItem>}
            </Select>
        </div>
    );
}
