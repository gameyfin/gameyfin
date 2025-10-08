import {useField} from "formik";
import {DatePicker, DateValue} from "@heroui/react";
import {parseDate} from "@internationalized/date";
import {useState} from "react";

// @ts-ignore
export default function DatePickerInput({label, showErrorUntouched = false, ...props}) {
    // @ts-ignore
    const [field, meta] = useField(props);
    const [value, setValue] = useState<DateValue | null>(field.value ? parseDate(field.value) : null);

    return (
        <DatePicker
            className="min-h-20 grow"
            showMonthAndYearPickers
            fullWidth={false}
            {...props}
            {...field}
            value={value}
            onChange={(date) => {
                setValue(date);
                field.onChange({
                    target: {
                        name: field.name,
                        value: date ? date.toString() : ''
                    }
                });
            }}
            id={label}
            label={label}
            isInvalid={(meta.touched || showErrorUntouched) && !!meta.error}
            errorMessage={meta.initialError || meta.error}
        />
    );
}