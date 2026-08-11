import {useField} from "formik";
import {FieldError, Label, TextArea, TextFieldProps, TextField} from "@heroui/react";

interface TextAreaInputProps extends Omit<TextFieldProps, "name" | "children"> {
    label?: string;
    name: string;
    showErrorUntouched?: boolean;
    placeholder?: string;
}

export default function TextAreaInput({label, showErrorUntouched = false, placeholder, ...props}: TextAreaInputProps) {
    const [field, meta, helpers] = useField(props.name);

    return (
        <TextField
            className={`grow ${meta.initialError || meta.error ? "" : "mb-6"}`}
            fullWidth={false}
            {...props}
            name={field.name}
            value={field.value}
            onChange={(value) => helpers.setValue(value)}
            onBlur={field.onBlur}
            isInvalid={(meta.touched || showErrorUntouched) && !!meta.error}
        >
            {label && <Label>{label}</Label>}
            <TextArea placeholder={placeholder}/>
            <FieldError>{meta.initialError || meta.error}</FieldError>
        </TextField>
    );
}
