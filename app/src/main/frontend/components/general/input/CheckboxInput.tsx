import {useField} from "formik";
import {Checkbox, CheckboxGroup, CheckboxProps, FieldError} from "@heroui/react";
import InfoPopup from "Frontend/components/administration/InfoPopup";
import ResetToDefaultButton from "Frontend/components/administration/ResetToDefaultButton";

interface CheckboxInputProps extends Omit<CheckboxProps, "name" | "children"> {
    label: string;
    name: string;
    description?: string;
    resetValue?: unknown;
}

export default function CheckboxInput({label, description, resetValue, className, ...props}: CheckboxInputProps) {
    const [field, meta] = useField({name: props.name, type: "checkbox"});

    return (
        <CheckboxGroup
            className={`flex flex-row flex-1 gap-2 ${className ?? ""}`}
            isInvalid={!!meta.error}
            value={field.value ? [field.name] : []}
        >
            <span className="flex items-center gap-1">
                <Checkbox
                    {...props}
                    isSelected={!!field.value}
                    onChange={(checked) => field.onChange({target: {name: field.name, checked, type: "checkbox"}})}
                    onBlur={field.onBlur}
                    name={field.name}
                    id={field.name}
                    value={field.name}
                    className="items-center"
                >
                    <Checkbox.Content>
                        <Checkbox.Control>
                            <Checkbox.Indicator/>
                        </Checkbox.Control>
                        {label}
                    </Checkbox.Content>
                </Checkbox>
                {description && <InfoPopup content={description}/>}
                {resetValue !== undefined &&
                    <ResetToDefaultButton fieldName={field.name} defaultValue={resetValue}/>}
            </span>
            <FieldError>{meta.initialError || meta.error}</FieldError>
        </CheckboxGroup>
    );
}

