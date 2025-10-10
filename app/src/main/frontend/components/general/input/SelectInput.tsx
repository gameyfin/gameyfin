import {useField} from "formik";
import {Select, SelectItem} from "@heroui/react";

// @ts-ignore
const SelectInput = ({label, values, ...props}) => {
    // @ts-ignore
    const [field, meta] = useField(props);

    const items = values.map((v: string) => ({key: v, label: v}));

    return (
        <div className="min-h-20 grow">
            <Select
                fullWidth={true}
                {...field}
                {...props}
                label={label}
                items={items}
                selectedKeys={[field.value]}
                isInvalid={!!meta.error}
                errorMessage={meta.initialError || meta.error}
                disallowEmptySelection
            >
                {(item: { key: string, label: string }) => <SelectItem>{item.label}</SelectItem>}
            </Select>
        </div>
    );
}

export default SelectInput;