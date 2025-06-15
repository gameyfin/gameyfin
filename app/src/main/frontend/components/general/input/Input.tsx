import {useField} from "formik";
import {Input as NextUiInput} from "@heroui/react";

// @ts-ignore
const Input = ({label, showErrorUntouched = false, ...props}) => {
    // @ts-ignore
    const [field, meta] = useField(props);

    return (
        <NextUiInput
            className="min-h-20 flex-grow"
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

export default Input;