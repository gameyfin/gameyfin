import {useField} from "formik";
import {NumberInput as HeroUiNumberInput, NumberInputProps} from "@heroui/react";
import InfoPopup from "Frontend/components/administration/InfoPopup";
import ResetToDefaultButton from "Frontend/components/administration/ResetToDefaultButton";

interface CustomNumberInputProps extends Omit<NumberInputProps, "name"> {
    name: string;
    showErrorUntouched?: boolean;
    resetValue?: unknown;
}

export default function NumberInput({
                                        label,
                                        showErrorUntouched = false,
                                        description,
                                        className,
                                        resetValue,
                                        ...props
                                    }: CustomNumberInputProps) {
    const [field, meta, helpers] = useField<number>(props.name);

    return (
        <HeroUiNumberInput
            fullWidth={false}
            {...props}
            className={`min-h-20 grow ${className ?? ""}`}
            value={field.value}
            onValueChange={(value) => helpers.setValue(value)}
            onBlur={field.onBlur}
            name={field.name}
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
