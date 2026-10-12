import {FieldArray, useField} from "formik";
import {Button, Chip, CloseButton, FieldError, Input, Label, Popover, TextField} from "@heroui/react";
import {KeyboardEvent, useState} from "react";
import {CheckIcon, PlusIcon} from "@phosphor-icons/react";
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
    const [isPopoverOpen, setPopoverOpen] = useState(false);

    return (
        <FieldArray name={field.name}
                    render={arrayHelpers => {
                        function addElements() {
                            newElementValue
                                .split(",")
                                .map((value) => value.trim())
                                .filter((value) => value !== "")
                                .forEach((value) => arrayHelpers.push(value));

                            setNewElementValue("");
                        }

                        function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
                            if (event.key === "Enter" || event.key == "Tab" || event.key === ",") {
                                event.preventDefault();
                                addElements();
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
                                        <Chip key={index} className={`items-center ${props.isDisabled ? "opacity-50" : "pr-0"}`}>
                                            {element}
                                            {!props.isDisabled &&
                                                <CloseButton
                                                    aria-label="Remove"
                                                    className="size-5 -my-1"
                                                    onPress={() => arrayHelpers.remove(index)}
                                                />}
                                        </Chip>
                                    ))}
                                    <Popover isOpen={isPopoverOpen} onOpenChange={setPopoverOpen}>
                                        <Button isIconOnly
                                                size="sm"
                                                variant="ghost"
                                                className="rounded-full"
                                                isDisabled={props.isDisabled}
                                        >
                                            <PlusIcon/>
                                        </Button>
                                        <Popover.Content placement="bottom">
                                            <Popover.Dialog>
                                                <Popover.Arrow/>
                                                <div className="flex flex-row items-center gap-2">
                                                    <TextField>
                                                        <Label className="sr-only">New element</Label>
                                                        <Input
                                                            variant="secondary"
                                                            value={newElementValue}
                                                            onChange={(e) => setNewElementValue(e.target.value)}
                                                            onKeyDown={handleKeyDown}
                                                            placeholder="New element..."
                                                        />
                                                        <FieldError/>
                                                    </TextField>
                                                    <Button isIconOnly
                                                            size="sm"
                                                            variant="tertiary"
                                                            aria-label="Add element"
                                                            onPress={() => {
                                                                addElements();
                                                                setPopoverOpen(false);
                                                            }}
                                                    >
                                                        <CheckIcon/>
                                                    </Button>
                                                </div>
                                            </Popover.Dialog>
                                        </Popover.Content>
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
