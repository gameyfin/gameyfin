import {useField} from "formik";
import {NumberInput as HeroUiNumberInput} from "@heroui/react";

// @ts-ignore
const NumberInput = ({label, showErrorUntouched = false, ...props}) => {
    // @ts-ignore
    const [field, meta, helpers] = useField(props);

    return (
        <HeroUiNumberInput
            className="min-h-20 grow"
            fullWidth={false}
            {...props}
            value={field.value}
            onValueChange={(value) => helpers.setValue(value)}
            onBlur={field.onBlur}
            name={field.name}
            id={label}
            label={label}
            isInvalid={(meta.touched || showErrorUntouched) && !!meta.error}
            errorMessage={meta.initialError || meta.error}
        />
    );
}

export default NumberInput;