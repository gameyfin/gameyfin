import ConfigEntryDto from "Frontend/generated/de/grimsi/gameyfin/config/dto/ConfigEntryDto";
import React from "react";
import Input from "Frontend/components/Input";
import CheckboxInput from "Frontend/components/CheckboxInput";

export default function ConfigFormField({configElement}: {
    configElement: ConfigEntryDto | undefined
}) {
    function inputElement(configElement: ConfigEntryDto) {
        switch (configElement.type) {
            case "Boolean":
                return (
                    <CheckboxInput label={configElement.description} name={configElement.key}/>
                );
            case "String":
                return (
                    <Input label={configElement.description} name={configElement.key} type="text"/>
                );
            case "Int" || "Float":
                return (
                    <Input label={configElement.description} name={configElement.key} type="number"/>
                );
            default:
                return <pre>Unsupported type: {configElement.type} for key {configElement.key}</pre>;
        }
    }

    return (inputElement(configElement!));
}