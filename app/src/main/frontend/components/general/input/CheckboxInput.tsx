import {useField} from "formik";
import {Checkbox, CheckboxGroup} from "@heroui/react";

// @ts-ignore
const CheckboxInput = ({label, ...props}) => {
    // @ts-ignore
    const [field, meta] = useField(props);

    return (
        <CheckboxGroup
            className="flex flex-row flex-1 items-baseline gap-2"
            isInvalid={!!meta.error}
            errorMessage={meta.initialError || meta.error}
            value={field.value ? [field.name] : []}
        >
            <Checkbox
                className="items-baseline"
                {...field}
                {...props}
                // @ts-ignore
                value={field.name}
            >
                {label}
            </Checkbox>
        </CheckboxGroup>
    );
}

export default CheckboxInput;