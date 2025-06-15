import {useField} from "formik";
import {Textarea} from "@heroui/react";

// @ts-ignore
export default function TextAreaInput({label, showErrorUntouched = false, ...props}) {
    // @ts-ignore
    const [field, meta] = useField(props);

    return (
        <Textarea
            className={`flex-grow ${meta.initialError || meta.error ? "" : "mb-6"}`}
            fullWidth={false}
            {...props}
            {...field}
            id={label}
            label={label}
            isInvalid={(meta.touched || showErrorUntouched) && !!meta.error}
            errorMessage={meta.initialError || meta.error}
        />
    );
}