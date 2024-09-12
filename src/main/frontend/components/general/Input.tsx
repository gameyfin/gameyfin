import {useField} from "formik";
import {Input as NextUiInput} from "@nextui-org/react";
import {SmallInfoField} from "Frontend/components/general/SmallInfoField";
import {XCircle} from "@phosphor-icons/react";

// @ts-ignore
const Input = ({label, ...props}) => {
    // @ts-ignore
    const [field, meta] = useField(props);

    return (
        <div className="flex flex-col flex-grow max-w-sm items-start gap-2 my-2">
            <NextUiInput
                {...props}
                {...field}
                id={label}
                label={label}
                isInvalid={meta.touched && !!meta.error}
            />
            <div className="min-h-6 w-full text-danger">
                {meta.touched && meta.error && (
                    <SmallInfoField icon={XCircle} message={meta.error}/>
                )}
            </div>
        </div>
    );
}

export default Input;