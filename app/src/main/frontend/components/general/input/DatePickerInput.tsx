import {useField} from "formik";
import {Calendar, DateField, DatePicker, DatePickerProps, DateValue, Description, FieldError, Label} from "@heroui/react";
import {parseDate} from "@internationalized/date";

interface DatePickerInputProps extends Omit<DatePickerProps<DateValue>, "name" | "children" | "onChange" | "value"> {
    name: string;
    label?: string;
    description?: string;
    showErrorUntouched?: boolean;
}

export default function DatePickerInput({label, description, showErrorUntouched = false, ...props}: DatePickerInputProps) {
    const [field, meta, helpers] = useField<string>(props.name);
    const value: DateValue | null = field.value ? parseDate(field.value) : null;

    return (
        <DatePicker
            className="min-h-20 grow"
            validationBehavior="aria"
            {...props}
            value={value}
            onChange={date => {
                void helpers.setTouched(true, false);
                void helpers.setValue(date ? date.toString() : '');
            }}
            onBlur={() => void helpers.setTouched(true)}
            name={field.name}
            isInvalid={(meta.touched || showErrorUntouched) && !!meta.error}
        >
            {label && <Label>{label}</Label>}
            <DateField.Group fullWidth>
                <DateField.Input>{(segment) => <DateField.Segment segment={segment}/>}</DateField.Input>
                <DateField.Suffix>
                    <DatePicker.Trigger>
                        <DatePicker.TriggerIndicator/>
                    </DatePicker.Trigger>
                </DateField.Suffix>
            </DateField.Group>
            <DatePicker.Popover>
                <Calendar aria-label={label as string}>
                    <Calendar.Header>
                        <Calendar.YearPickerTrigger>
                            <Calendar.YearPickerTriggerHeading/>
                            <Calendar.YearPickerTriggerIndicator/>
                        </Calendar.YearPickerTrigger>
                        <Calendar.NavButton slot="previous"/>
                        <Calendar.NavButton slot="next"/>
                    </Calendar.Header>
                    <Calendar.Grid>
                        <Calendar.GridHeader>
                            {(day) => <Calendar.HeaderCell>{day}</Calendar.HeaderCell>}
                        </Calendar.GridHeader>
                        <Calendar.GridBody>{(date) => <Calendar.Cell date={date}/>}</Calendar.GridBody>
                    </Calendar.Grid>
                    <Calendar.YearPickerGrid>
                        <Calendar.YearPickerGridBody>
                            {({year}) => <Calendar.YearPickerCell year={year}/>}
                        </Calendar.YearPickerGridBody>
                    </Calendar.YearPickerGrid>
                </Calendar>
            </DatePicker.Popover>
            {description && <Description className="w-0 min-w-full">{description}</Description>}
            <FieldError>{meta.error}</FieldError>
        </DatePicker>
    );
}
