import {useField} from "formik";
import {Description, FieldError, Label, NumberField, NumberFieldProps} from "@heroui/react";
import InfoPopup from "Frontend/components/administration/InfoPopup";
import ResetToDefaultButton from "Frontend/components/administration/ResetToDefaultButton";

interface CustomNumberInputProps extends Omit<NumberFieldProps, "name" | "children"> {
    name: string;
    label?: string;
    showErrorUntouched?: boolean;
    resetValue?: unknown;
    description?: string;
}

export default function NumberInput({
                                        label,
                                        showErrorUntouched = false,
                                        description,
                                        className,
                                        resetValue,
                                        ...props
                                    }: CustomNumberInputProps) {
    const [field, meta, helpers] = useField<number>(props.name);

    return (
        <NumberField
            fullWidth={false}
            {...props}
            className={`min-h-20 grow ${className ?? ""}`}
            value={field.value}
            onChange={(value) => helpers.setValue(value)}
            onBlur={field.onBlur}
            name={field.name}
            isInvalid={(meta.touched || showErrorUntouched) && !!meta.error}
        >
            {label && <Label>{label}</Label>}
            <div className="flex items-center gap-1">
                <NumberField.Group className="grow">
                    <NumberField.DecrementButton/>
                    <NumberField.Input/>
                    <NumberField.IncrementButton/>
                </NumberField.Group>
                {(description || resetValue !== undefined) && (
                    <span className="flex items-center gap-1">
                        {description && <InfoPopup content={description as string}/>}
                        {resetValue !== undefined &&
                            <ResetToDefaultButton fieldName={field.name} defaultValue={resetValue}/>}
                    </span>
                )}
            </div>
            {description && <Description className="sr-only">{description as string}</Description>}
            <FieldError>{meta.initialError || meta.error}</FieldError>
        </NumberField>
    );
}
