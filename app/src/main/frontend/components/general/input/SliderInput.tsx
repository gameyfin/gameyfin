import {useField} from "formik";
import {Slider as HeroUiSlider} from "@heroui/react";

// @ts-ignore
const SliderInput = ({label, showErrorUntouched = false, ...props}) => {
    // @ts-ignore
    const [field, meta, helpers] = useField(props);

    return (
        <HeroUiSlider
            className="min-h-20 grow"
            {...props}
            value={field.value}
            onChange={(value) => helpers.setValue(value)}
            onBlur={field.onBlur}
            name={field.name}
            id={label}
            label={label}
        />
    );
}

export default SliderInput;