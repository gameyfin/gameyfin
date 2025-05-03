import {FieldArray, useField} from "formik";
import {Button, Chip, Input, Popover, PopoverContent, PopoverTrigger} from "@heroui/react";
import {KeyboardEvent, useState} from "react";
import {Plus, XCircle} from "@phosphor-icons/react";
import {SmallInfoField} from "Frontend/components/general/SmallInfoField";

// @ts-ignore
const ArrayInput = ({label, ...props}) => {
    // @ts-ignore
    const [field, meta] = useField(props);
    const [newElementValue, setNewElementValue] = useState<string>("");

    return (
        <FieldArray name={field.name}
                    render={arrayHelpers => {
                        function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
                            if (event.key === "Enter" || event.key == "Tab" || event.key === ",") {
                                event.preventDefault();
                                
                                newElementValue
                                    .split(",")
                                    .map((value) => value.trim())
                                    .filter((value) => value !== "")
                                    .forEach((value) => arrayHelpers.push(value));

                                setNewElementValue("");
                            }
                        }

                        return (
                            <div className="flex flex-col gap-2">
                                <div className="flex flex-row justify-between">
                                    <p>{label}</p>
                                    <small>{field.value.length} {field.value.length == 1 ? "element" : "elements"}</small>
                                </div>

                                <div className="flex flex-row flex-wrap gap-2 items-center">
                                    {field.value.map((element: any, index: number) => (
                                        <Chip key={index} onClose={() => arrayHelpers.remove(index)}>
                                            {element}
                                        </Chip>
                                    ))}
                                    <Popover placement="bottom" showArrow={true}>
                                        <PopoverTrigger>
                                            <Button isIconOnly size="sm" variant="light" radius="full"><Plus/></Button>
                                        </PopoverTrigger>
                                        <PopoverContent>
                                            <Input
                                                value={newElementValue}
                                                onChange={(e) => setNewElementValue(e.target.value)}
                                                onKeyDown={handleKeyDown}
                                                placeholder="New element..."
                                                variant="bordered"
                                            />
                                        </PopoverContent>
                                    </Popover>
                                </div>

                                <div className="min-h-6 text-danger">
                                    {meta.touched && meta.error && meta.error.trim().length > 0 && (
                                        <SmallInfoField icon={XCircle} message={meta.error}/>
                                    )}
                                </div>
                            </div>
                        );
                    }}
        />
    );
}

export default ArrayInput;