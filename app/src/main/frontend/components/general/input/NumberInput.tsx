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
        <div className={`min-h-20 grid grid-cols-[1fr_auto] items-center gap-1 ${className ?? ""}`}>
            <NumberField
                fullWidth={false}
                {...props}
                className="contents"
                value={field.value}
                onChange={(value) => helpers.setValue(value)}
                onBlur={field.onBlur}
                name={field.name}
                isInvalid={(meta.touched || showErrorUntouched) && !!meta.error}
            >
                {label && <Label className="col-span-2">{label}</Label>}
                <NumberField.Group className="grow">
                    <NumberField.DecrementButton/>
                    <NumberField.Input/>
                    <NumberField.IncrementButton/>
                </NumberField.Group>
                {description && <Description className="sr-only">{description as string}</Description>}
                <FieldError className="col-span-2">{meta.initialError || meta.error}</FieldError>
            </NumberField>
            {(description || resetValue !== undefined) && (
                <span className="flex items-center gap-1">
                    {description && <InfoPopup content={description as string}/>}
                    {resetValue !== undefined &&
                        <ResetToDefaultButton fieldName={field.name} defaultValue={resetValue}/>}
                </span>
            )}
        </div>
    );
}
