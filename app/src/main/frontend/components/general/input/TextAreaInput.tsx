import {useField} from "formik";
import {Textarea, TextAreaProps} from "@heroui/react";

interface TextAreaInputProps extends Omit<TextAreaProps, "name"> {
    name: string;
    showErrorUntouched?: boolean;
}

export default function TextAreaInput({label, showErrorUntouched = false, ...props}: TextAreaInputProps) {
    const [field, meta] = useField(props.name);

    return (
        <Textarea
            className={`grow ${meta.initialError || meta.error ? "" : "mb-6"}`}
            fullWidth={false}
            {...props}
            {...field}
            id={label as string}
            label={label}
            isInvalid={(meta.touched || showErrorUntouched) && !!meta.error}
            errorMessage={meta.initialError || meta.error}
        />
    );
}