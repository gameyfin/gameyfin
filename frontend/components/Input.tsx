import {useField} from "formik";
import {XCircle} from "@phosphor-icons/react";
import {Input as ShadcnInput} from "Frontend/@/components/ui/input";
import {Label} from "Frontend/@/components/ui/label";

// @ts-ignore
const Input = ({label, ...props}) => {
    // @ts-ignore
    const [field, meta] = useField(props);

    return (
        <div className="grid w-full max-w-sm items-center gap-1.5">
            <Label htmlFor={label}>{label}</Label>
            <ShadcnInput
                {...props}
                {...field}
                id={label}
            />
            {(meta.touched && !!meta.error) ?
                <small
                    className="flex flex-row items-center gap-1 text-red-500"
                >
                    <XCircle weight="fill" size={14}/> {meta.error}
                </small> : <></>
            }
        </div>
    );
}

export default Input;