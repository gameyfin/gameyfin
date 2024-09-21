import {useField} from "formik";
import {Select, SelectItem} from "@nextui-org/react";

// @ts-ignore
const SelectInput = ({label, values, ...props}) => {
    // @ts-ignore
    const [field] = useField(props);

    return (
        <div className="flex flex-row flex-1 justify-center gap-2 my-2">
            <Select
                {...field}
                {...props}
                id={field.name}
                label={label}
                defaultSelectedKeys={[field.value]}
                disallowEmptySelection
            >
                {values.map((value: string) => (
                    <SelectItem key={value} value={value}>
                        {value}
                    </SelectItem>
                ))}
            </Select>
        </div>
    );
}

export default SelectInput;