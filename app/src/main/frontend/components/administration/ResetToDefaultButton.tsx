import {Button, Tooltip} from "@heroui/react";
import {ArrowUUpLeftIcon} from "@phosphor-icons/react";
import {useFormikContext} from "formik";

interface ResetToDefaultButtonProps {
    fieldName: string;
    defaultValue: unknown;
}

function valuesEqual(a: unknown, b: unknown): boolean {
    if (a === b) return true;
    if (Array.isArray(a) && Array.isArray(b)) {
        if (a.length !== b.length) return false;
        return a.every((val, i) => valuesEqual(val, b[i]));
    }
    return false;
}

function formatDefaultValue(value: unknown): string {
    const str = Array.isArray(value) ? value.join(", ") : String(value ?? "");
    return str.length > 25 ? str.substring(0, 25) + "…" : str;
}

export default function ResetToDefaultButton({fieldName, defaultValue}: ResetToDefaultButtonProps) {
    const {setFieldValue, getFieldMeta} = useFormikContext();
    const currentValue = getFieldMeta(fieldName).value;
    const isDefault = valuesEqual(currentValue, defaultValue);

    return (
        <Tooltip delay={0}>
            <Tooltip.Trigger>
                <Button
                    isIconOnly
                    size="sm"
                    variant="tertiary"
                    isDisabled={isDefault}
                    className="-ml-2 z-50 rounded-full"
                    onPress={() => setFieldValue(fieldName, defaultValue)}
                >
                    <ArrowUUpLeftIcon size={16}/>
                </Button>
            </Tooltip.Trigger>
            <Tooltip.Content placement="right">
                <span>Reset to default: <pre className="inline">{formatDefaultValue(defaultValue)}</pre></span>
            </Tooltip.Content>
        </Tooltip>
    );
}
