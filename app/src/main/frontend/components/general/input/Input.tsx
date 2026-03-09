import {useField} from "formik";
import {Input as HeroUiInput, InputProps} from "@heroui/react";
import InfoPopup from "Frontend/components/administration/InfoPopup";
import ResetToDefaultButton from "Frontend/components/administration/ResetToDefaultButton";

interface CustomInputProps extends Omit<InputProps, "name"> {
    name: string;
    showErrorUntouched?: boolean;
    resetValue?: unknown;
}

export default function Input({
                                  label,
                                  showErrorUntouched = false,
                                  description,
                                  className,
                                  resetValue,
                                  ...props
                              }: CustomInputProps) {
    const [field, meta] = useField(props.name);

    return (
        <HeroUiInput
            fullWidth={false}
            {...props}
            {...field}
            className={`min-h-20 grow ${className ?? ""}`}
            id={label as string}
            label={label}
            endContent={
                (description || resetValue !== undefined) ? (
                    <span className="flex items-center gap-1">
                        {description && <InfoPopup content={description as string}/>}
                        {resetValue !== undefined &&
                            <ResetToDefaultButton fieldName={field.name} defaultValue={resetValue}/>}
                    </span>
                ) : undefined
            }
            isInvalid={(meta.touched || showErrorUntouched) && !!meta.error}
            errorMessage={meta.initialError || meta.error}
        />
    );
}