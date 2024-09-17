import ConfigEntryDto from "Frontend/generated/de/grimsi/gameyfin/config/dto/ConfigEntryDto";
import React from "react";
import Input from "Frontend/components/general/Input";
import CheckboxInput from "Frontend/components/general/CheckboxInput";
import SelectInput from "Frontend/components/general/SelectInput";

export default function ConfigFormField({configElement, ...props}: any) {
    function inputElement(configElement: ConfigEntryDto) {

        if (configElement.allowedValues != null && configElement.allowedValues.length > 0) {
            return (
                <SelectInput label={configElement.description} name={configElement.key}
                             values={configElement.allowedValues} {...props}/>
            );
        }

        switch (configElement.type) {
            case "Boolean":
                return (
                    <CheckboxInput label={configElement.description} name={configElement.key} {...props}/>
                );
            case "String":
                return (
                    <Input label={configElement.description} name={configElement.key} type="text" {...props}/>
                );
            case "Float":
                return (
                    <Input label={configElement.description} name={configElement.key} type="number"
                           step="0.1" {...props}/>
                );
            case "Int":
                return (
                    <Input label={configElement.description} name={configElement.key} type="number"
                           step="1" {...props}/>
                );
            default:
                return <pre>Unsupported type: {configElement.type} for key {configElement.key}</pre>;
        }
    }

    return (inputElement(configElement!));
}