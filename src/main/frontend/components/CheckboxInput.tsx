import {useField} from "formik";
import {Checkbox} from "@nextui-org/react";

// @ts-ignore
const CheckboxInput = ({label, ...props}) => {
    // @ts-ignore
    const [field] = useField(props);

    return (
        <div className="flex flex-row flex-grow items-center gap-2 my-2">
            <Checkbox
                {...field}
                id={field.name}>
                {label}
            </Checkbox>
        </div>
    );
}

export default CheckboxInput;