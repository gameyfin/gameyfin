import SelectInput from "Frontend/components/general/input/SelectInput";
import CheckboxInput from "Frontend/components/general/input/CheckboxInput";
import Input from "Frontend/components/general/input/Input";
import NumberInput from "Frontend/components/general/input/NumberInput";
import React from "react";
import PluginConfigMetadataDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/PluginConfigMetadataDto";

export default function PluginConfigFormField({pluginConfigMetadata, ...props}: any) {
    function inputElement(metadata: PluginConfigMetadataDto) {
        let defaultValue = metadata.default;
        if (defaultValue != null) {
            switch (metadata.type.toLowerCase()) {
                case "float":
                case "int":
                    defaultValue = Number(defaultValue);
                    break;
                case "boolean":
                    defaultValue = defaultValue === true || defaultValue === "true";
                    break;
                default:
                    defaultValue = String(defaultValue);
            }
        }
        const commonProps = {
            ...props,
            description: metadata.description,
            resetValue: defaultValue ?? undefined
        };

        if (metadata.allowedValues != null && metadata.allowedValues.length > 0) {
            return (
                <SelectInput label={metadata.label}
                             name={metadata.key}
                             values={metadata.allowedValues}
                             isRequired={metadata.required}
                             {...commonProps}/>
            );
        }

        switch (metadata.type.toLowerCase()) {
            case "boolean":
                return (
                    <CheckboxInput label={metadata.label}
                                   name={metadata.key}
                                   {...commonProps}/>
                );
            case "string":
                return (
                    <Input label={metadata.label}
                           name={metadata.key}
                           type={metadata.secret ? "password" : "text"}
                           isRequired={metadata.required}
                           {...commonProps}/>
                );
            case "float":
                return (
                    <NumberInput label={metadata.label}
                           name={metadata.key}
                           isRequired={metadata.required}
                           step={0.1}
                           {...commonProps}/>
                );
            case "int":
                return (
                    <NumberInput label={metadata.label}
                           name={metadata.key}
                           isRequired={metadata.required}
                           step={1}
                           {...commonProps}/>
                );
            default:
                return <pre>Unsupported type: {metadata.type} for key {metadata.key}</pre>;
        }
    }

    return inputElement(pluginConfigMetadata!);
}
