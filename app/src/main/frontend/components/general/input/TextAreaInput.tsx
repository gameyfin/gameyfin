import {useField} from "formik";
import {ComponentProps, ReactNode, Ref} from "react";
import {FieldError, Label, TextArea, TextFieldProps, TextField} from "@heroui/react";

export interface TextAreaInputProps extends Omit<TextFieldProps, "name" | "children"> {
    textareaRef?: Ref<HTMLTextAreaElement>;
    textareaProps?: ComponentProps<typeof TextArea>;
    toolbar?: ReactNode;
    preview?: ReactNode;
    renderEditor?: (textarea: ReactNode) => ReactNode;
    label?: string;
    labelSuffix?: ReactNode;
    name: string;
    showErrorUntouched?: boolean;
    placeholder?: string;
}

export default function TextAreaInput({label, labelSuffix, showErrorUntouched = false, placeholder, textareaRef, textareaProps, toolbar, preview, renderEditor, ...props}: TextAreaInputProps) {
    const [field, meta, helpers] = useField(props.name);
    const textarea = <TextArea ref={textareaRef} placeholder={placeholder} {...textareaProps} hidden={preview != null}/>;

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
            {toolbar}
            {renderEditor ? renderEditor(textarea) : textarea}
            {preview}
            <FieldError>{meta.initialError || meta.error}</FieldError>
        </TextField>
    );
}
