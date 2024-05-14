import {useField} from "formik";
import {XCircle} from "@phosphor-icons/react";
import {Input as NextUiInput} from "@nextui-org/react";

// @ts-ignore
const Input = ({label, ...props}) => {
    // @ts-ignore
    const [field, meta] = useField(props);

    return (
        <div className="grid w-full max-w-sm items-center gap-1.5">
            <NextUiInput
                {...props}
                {...field}
                id={label}
                placeholder={label}
                isInvalid={meta.touched && !!meta.error}
                errorMessage={
                    <small className="flex flex-row items-center gap-1 text-danger">
                        <XCircle weight="fill" size={14}/> {meta.error}
                    </small>}
            />
        </div>
    );
}

export default Input;