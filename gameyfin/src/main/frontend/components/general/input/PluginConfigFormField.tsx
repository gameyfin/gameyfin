import SelectInput from "Frontend/components/general/input/SelectInput";
import CheckboxInput from "Frontend/components/general/input/CheckboxInput";
import Input from "Frontend/components/general/input/Input";
import React from "react";
import PluginConfigMetadataDto from "Frontend/generated/de/grimsi/gameyfin/core/plugins/dto/PluginConfigMetadataDto";

export default function PluginConfigFormField({pluginConfigMetadata, ...props}: any) {
    function inputElement(metadata: PluginConfigMetadataDto) {

        if (metadata.allowedValues != null && metadata.allowedValues.length > 0) {
            return (
                <SelectInput label={metadata.label}
                             name={metadata.key}
                             values={metadata.allowedValues}
                             {...props}/>
            );
        }

        switch (metadata.type) {
            case "Boolean":
                return (
                    <CheckboxInput label={metadata.label}
                                   name={metadata.key}
                                   {...props}/>
                );
            case "String":
                return (
                    <Input label={metadata.label}
                           name={metadata.key}
                           type={metadata.secret ? "password" : "text"}
                           isRequired={metadata.required}
                           {...props}/>
                );
            case "Float":
                return (
                    <Input label={metadata.label}
                           name={metadata.key}
                           type="number"
                           isRequired={metadata.required}
                           step="0.1"
                           {...props}/>
                );
            case "Int":
                return (
                    <Input label={metadata.label}
                           name={metadata.key}
                           type="number"
                           isRequired={metadata.required}
                           step="1"
                           {...props}/>
                );
            default:
                return <pre>Unsupported type: {metadata.type} for key {metadata.key}</pre>;
        }
    }

    return inputElement(pluginConfigMetadata!);
}