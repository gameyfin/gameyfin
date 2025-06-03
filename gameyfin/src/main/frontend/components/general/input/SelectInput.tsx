import {useField} from "formik";
import {Select, SelectItem} from "@heroui/react";

// @ts-ignore
const SelectInput = ({label, values, ...props}) => {
    // @ts-ignore
    const [field] = useField(props);

    const items = values.map((v: string) => ({key: v, label: v}));

    return (
        <Select
            {...field}
            {...props}
            label={label}
            items={items}
            selectedKeys={[field.value]}
            disallowEmptySelection
        >
            {(item: { key: string, label: string }) => <SelectItem>{item.label}</SelectItem>}
        </Select>
    );
}

export default SelectInput;