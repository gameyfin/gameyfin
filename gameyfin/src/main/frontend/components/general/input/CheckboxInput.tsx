import {useField} from "formik";
import {Checkbox} from "@heroui/react";

// @ts-ignore
const CheckboxInput = ({label, ...props}) => {
    // @ts-ignore
    const [field] = useField(props);

    return (
        <div className="flex flex-row flex-1 items-center gap-2 mb-6">
            <Checkbox
                {...field}
                {...props}
                id={field.name}
                isSelected={field.value}
            >
                {label}
            </Checkbox>
        </div>
    );
}

export default CheckboxInput;