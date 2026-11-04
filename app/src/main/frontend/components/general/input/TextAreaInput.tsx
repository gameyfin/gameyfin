import {useField} from "formik";
import {ReactNode} from "react";
import {FieldError, Label, TextArea, TextFieldProps, TextField} from "@heroui/react";

interface TextAreaInputProps extends Omit<TextFieldProps, "name" | "children"> {
    label?: string;
    labelSuffix?: ReactNode;
    name: string;
    showErrorUntouched?: boolean;
    placeholder?: string;
}

export default function TextAreaInput({label, labelSuffix, showErrorUntouched = false, placeholder, ...props}: TextAreaInputProps) {
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
            {label && <Label className={labelSuffix ? "flex items-center after:order-1" : undefined}>
                {label}
                {labelSuffix && <span className="order-2 inline-flex">{labelSuffix}</span>}
            </Label>}
            <TextArea placeholder={placeholder}/>
            <FieldError>{meta.initialError || meta.error}</FieldError>
        </TextField>
    );
}
