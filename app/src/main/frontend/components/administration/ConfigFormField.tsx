import ConfigEntryDto from "Frontend/generated/org/gameyfin/app/config/dto/ConfigEntryDto";
import React from "react";
import Input from "Frontend/components/general/input/Input";
import CheckboxInput from "Frontend/components/general/input/CheckboxInput";
import SelectInput from "Frontend/components/general/input/SelectInput";
import ArrayInput from "Frontend/components/general/input/ArrayInput";

export default function ConfigFormField({configElement, ...props}: any) {
    function inputElement(configElement: ConfigEntryDto) {

        if (configElement.allowedValues != null && configElement.allowedValues.length > 0) {
            return (
                <SelectInput label={configElement.description} name={configElement.key}
                             values={configElement.allowedValues} {...props}/>
            );
        }

        switch (configElement.type.toLowerCase()) {
            case "boolean":
                return (
                    <CheckboxInput label={configElement.description} name={configElement.key} {...props}/>
                );
            case "string":
                return (
                    <Input label={configElement.description} name={configElement.key}
                           type={props.type && "text"} {...props}/>
                );
            case "float":
                return (
                    <Input label={configElement.description} name={configElement.key} type="number"
                           step="0.1" {...props}/>
                );
            case "int":
                return (
                    <Input label={configElement.description} name={configElement.key} type="number"
                           step="1" {...props}/>
                );
            case "array":
                return (
                    <ArrayInput label={configElement.description} name={configElement.key} type="text" {...props}/>
                );
            default:
                return <pre>Unsupported type: {configElement.type} for key {configElement.key}</pre>;
        }
    }

    return inputElement(configElement!);
}