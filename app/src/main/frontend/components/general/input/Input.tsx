import {useField} from "formik";
import {Description, FieldError, Input as HeroUiInput, Label, TextField, TextFieldProps} from "@heroui/react";
import InfoPopup from "Frontend/components/administration/InfoPopup";
import ResetToDefaultButton from "Frontend/components/administration/ResetToDefaultButton";

interface CustomInputProps extends Omit<TextFieldProps, "name" | "children"> {
    name: string;
    label?: string;
    showErrorUntouched?: boolean;
    resetValue?: unknown;
    description?: string;
    type?: string;
    autoComplete?: string;
    placeholder?: string;
}

export default function Input({
                                  label,
                                  showErrorUntouched = false,
                                  description,
                                  className,
                                  resetValue,
                                  type,
                                  autoComplete,
                                  placeholder,
                                  ...props
                              }: CustomInputProps) {
    const [field, meta] = useField(props.name);

    return (
        <TextField
            fullWidth={false}
            {...props}
            {...field}
            className={`min-h-20 grow ${className ?? ""}`}
            isInvalid={(meta.touched || showErrorUntouched) && !!meta.error}
        >
            {label && <Label>{label}</Label>}
            <div className="flex items-center gap-1">
                <HeroUiInput className="grow" type={type} autoComplete={autoComplete} placeholder={placeholder}/>
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
        </TextField>
    );
}