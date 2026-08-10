import {useField} from "formik";
import {Label, Slider as HeroUiSlider, SliderProps} from "@heroui/react";
import InfoPopup from "Frontend/components/administration/InfoPopup";
import ResetToDefaultButton from "Frontend/components/administration/ResetToDefaultButton";

interface SliderInputProps extends Omit<SliderProps, "name" | "children" | "label"> {
    name: string;
    label?: string;
    description?: string;
    showErrorUntouched?: boolean;
    resetValue?: unknown;
}

export default function SliderInput({
                                        label,
                                        showErrorUntouched = false,
                                        description,
                                        resetValue,
                                        ...props
                                    }: SliderInputProps) {
    const [field, , helpers] = useField<number>(props.name);

    return (
        <HeroUiSlider
            className="min-h-20 grow"
            {...props}
            value={field.value}
            onChange={(value) => helpers.setValue(value as number)}
        >
            <Label className="flex items-center gap-1">
                {label}
                {description && <InfoPopup content={description}/>}
                {resetValue !== undefined &&
                    <ResetToDefaultButton fieldName={field.name} defaultValue={resetValue}/>}
            </Label>
            <HeroUiSlider.Output/>
            <HeroUiSlider.Track>
                <HeroUiSlider.Fill/>
                <HeroUiSlider.Thumb/>
            </HeroUiSlider.Track>
        </HeroUiSlider>
    );
}
