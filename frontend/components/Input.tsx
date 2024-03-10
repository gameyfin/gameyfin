import {useField} from "formik";
import {Input as MaterialInput, Typography} from "@material-tailwind/react";
import {XCircle} from "@phosphor-icons/react";

// @ts-ignore
const Input = ({label, ...props}) => {
    // @ts-ignore
    const [field, meta] = useField(props);

    return (
        <>
            <MaterialInput
                {...props}
                {...field}
                label={label}
                error={meta.touched && !!meta.error}
                success={meta.touched && !meta.error}
                crossOrigin=""
            />
            {(meta.touched && !!meta.error) ?
                <Typography
                    variant="small"
                    color="red"
                    className="ml-3 -mt-5 flex flex-row items-center gap-1"
                >
                    <XCircle weight="fill" size={14}/> {meta.error}
                </Typography> : <></>
            }
        </>
    );
}

export default Input;
/*
                        <Input
                            onChange={(event) => {
                                setUsername(event.target.value);
                            }}
                            id="username"
                            type="text"
                            autoComplete="username"
                            placeholder=""
                            size="lg"
                            className=" !border-t-blue-gray-200 focus:!border-t-gray-900"
                            labelProps={{
                                className: "before:content-none after:content-none",
                            }}
                            crossOrigin="" //TODO: see https://github.com/creativetimofficial/material-tailwind/issues/427
                        />
*/