import ConfigEntryDto from "Frontend/generated/org/gameyfin/app/config/dto/ConfigEntryDto";
import React from "react";
import Input from "Frontend/components/general/input/Input";
import CheckboxInput from "Frontend/components/general/input/CheckboxInput";
import SelectInput from "Frontend/components/general/input/SelectInput";
import ArrayInput from "Frontend/components/general/input/ArrayInput";
import NumberInput from "Frontend/components/general/input/NumberInput";
import SliderInput from "Frontend/components/general/input/SliderInput";

interface ConfigFormFieldProps {
    configElement?: ConfigEntryDto;
    className?: string;
    isDisabled?: boolean;
    type?: string;
}

type CommonInputProps = Pick<ConfigFormFieldProps, "className" | "isDisabled">;

export default function ConfigFormField({configElement, type: inputType, className, isDisabled}: ConfigFormFieldProps) {
    function inputElement(configElement: ConfigEntryDto) {
        const commonProps: CommonInputProps = {className, isDisabled};
        const description = configElement.description;
        const defaultValue = configElement.defaultValue;

        if (configElement.allowedValues != null && configElement.allowedValues.length > 0) {
            return (
                <SelectInput label={configElement.name} name={configElement.key}
                             description={description} resetValue={defaultValue}
                             values={configElement.allowedValues} {...commonProps}/>
            );
        }

        switch (configElement.type.toLowerCase()) {
            case "boolean":
                return (
                    <CheckboxInput label={configElement.name} name={configElement.key}
                                   description={description} resetValue={defaultValue} {...commonProps}/>
                );
            case "string":
                return (
                    <Input label={configElement.name} name={configElement.key}
                           description={description} resetValue={defaultValue}
                           type={inputType ?? "text"} {...commonProps}/>
                );
            case "float":
                return (
                    <NumberInput label={configElement.name} name={configElement.key}
                                 description={description} resetValue={defaultValue}
                                 step={0.1} {...commonProps}/>
                );
            case "int":
                if (configElement.min != null && configElement.max != null) {
                    return (
                        <SliderInput label={configElement.name} name={configElement.key}
                                     description={description} resetValue={defaultValue}
                                     minValue={configElement.min as number}
                                     maxValue={configElement.max as number}
                                     step={(configElement.step as number) ?? 1}
                                     {...commonProps}/>
                    );
                }
                return (
                    <NumberInput label={configElement.name} name={configElement.key}
                                 description={description} resetValue={defaultValue}
                                 step={1} {...commonProps}/>
                );
            case "array":
                return (
                    <ArrayInput label={configElement.name} name={configElement.key}
                                description={description} resetValue={defaultValue}
                                type="text" {...commonProps}/>
                );
            default:
                return <pre>Unsupported type: {configElement.type} for key {configElement.key}</pre>;
        }
    }

    return inputElement(configElement!);
}
