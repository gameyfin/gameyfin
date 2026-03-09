import {FieldArray, useField} from "formik";
import {Button, Chip, Input, Popover, PopoverContent, PopoverTrigger} from "@heroui/react";
import {KeyboardEvent, useState} from "react";
import {PlusIcon} from "@phosphor-icons/react";
import InfoPopup from "Frontend/components/administration/InfoPopup";
import ResetToDefaultButton from "Frontend/components/administration/ResetToDefaultButton";

interface ArrayInputProps {
    label: string;
    name: string;
    type?: string;
    description?: string;
    resetValue?: unknown;
    isDisabled?: boolean;
}

export default function ArrayInput({label, description, resetValue, ...props}: ArrayInputProps) {
    const [field, meta] = useField<string[]>(props.name);
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
                            <div className="flex flex-col flex-1 gap-2">
                                <div className="flex flex-row justify-between">
                                    <span className="flex items-center gap-1">
                                        <p>{label}</p>
                                        {description && <InfoPopup content={description}/>}
                                        {resetValue !== undefined &&
                                            <ResetToDefaultButton fieldName={field.name} defaultValue={resetValue}/>}
                                    </span>
                                    <small>{field.value.length} {field.value.length == 1 ? "element" : "elements"}</small>
                                </div>

                                <div className="flex flex-row flex-wrap gap-2 items-center">
                                    {field.value.map((element: string, index: number) => (
                                        <Chip key={index}
                                              onClose={() => arrayHelpers.remove(index)}
                                              isDisabled={props.isDisabled}
                                        >
                                            {element}
                                        </Chip>
                                    ))}
                                    <Popover placement="bottom" showArrow={true}>
                                        <PopoverTrigger>
                                            <Button isIconOnly
                                                    size="sm"
                                                    variant="light"
                                                    radius="full"
                                                    isDisabled={props.isDisabled}
                                            >
                                                <PlusIcon/>
                                            </Button>
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
                                        meta.error
                                    )}
                                </div>
                            </div>
                        );
                    }}
        />
    );
}
