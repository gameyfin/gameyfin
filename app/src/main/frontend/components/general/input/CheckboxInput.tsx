import {useField} from "formik";
import {Checkbox, CheckboxGroup, CheckboxProps} from "@heroui/react";
import InfoPopup from "Frontend/components/administration/InfoPopup";
import ResetToDefaultButton from "Frontend/components/administration/ResetToDefaultButton";

interface CheckboxInputProps extends Omit<CheckboxProps, "name"> {
    label: string;
    name: string;
    description?: string;
    resetValue?: unknown;
}

export default function CheckboxInput({label, description, resetValue, ...props}: CheckboxInputProps) {
    const [field, meta] = useField({name: props.name, type: "checkbox"});

    return (
        <CheckboxGroup
            className="flex flex-row flex-1 gap-2"
            isInvalid={!!meta.error}
            errorMessage={meta.initialError || meta.error}
            value={field.value ? [field.name] : []}
        >
            <span className="flex items-center gap-1">
                <Checkbox
                    {...field}
                    {...props}
                    className="items-center"
                    value={field.name}
                >
                    {label}
                </Checkbox>
                {description && <InfoPopup content={description}/>}
                {resetValue !== undefined &&
                    <ResetToDefaultButton fieldName={field.name} defaultValue={resetValue}/>}
            </span>
        </CheckboxGroup>
    );
}

