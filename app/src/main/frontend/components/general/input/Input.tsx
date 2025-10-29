import {useField} from "formik";
import {Input as HeroUiInput} from "@heroui/react";

// @ts-ignore
const Input = ({label, showErrorUntouched = false, ...props}) => {
    // @ts-ignore
    const [field, meta] = useField(props);

    return (
        <HeroUiInput
            className="min-h-20 grow"
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