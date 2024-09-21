import {useField} from "formik";
import {Checkbox} from "@nextui-org/react";

// @ts-ignore
const CheckboxInput = ({label, ...props}) => {
    // @ts-ignore
    const [field] = useField(props);

    return (
        <div className="flex flex-row flex-1 items-center gap-2">
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