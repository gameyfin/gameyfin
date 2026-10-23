import {ReactElement, useEffect, useState} from "react";
import {Button, ButtonGroup, Description, Dropdown, Key, Label} from "@heroui/react";
import {CaretDownIcon, IconProps} from "@phosphor-icons/react";
import {useUserPreferenceService} from "Frontend/util/user-preference-service";

export interface ComboButtonOption {
    label: string;
    description: string;
    action: () => void;
    isDisabled?: boolean;
}

export interface ComboButtonProps {
    description?: string;
    icon?: ReactElement<IconProps>;
    options: Record<string, ComboButtonOption>;
    preferredOptionKey?: string;
}

export default function ComboButton({options, preferredOptionKey, description, icon}: ComboButtonProps) {
    const [selectedOption, setSelectedOption] = useState(new Set([Object.keys(options)[0]]));
    const selectedOptionValue = Array.from(selectedOption)[0];
    const hasMultipleOptions = Object.keys(options).length > 1;
    const userPreferenceService = useUserPreferenceService();

    useEffect(() => {
        if (!preferredOptionKey) return;

        userPreferenceService.get(preferredOptionKey).then((key) => {
            if (key && options[key]) {
                setSelectedOption(new Set([key]));
            } else {
                setSelectedOption(new Set([Object.keys(options)[0]]));
            }
        })
    }, []);

    async function onSelectionChange(keys: Set<Key> | "all") {
        if (keys === "all") return;
        const currentKey = Array.from(keys)[0];
        if (!currentKey) return;

        if (preferredOptionKey) {
            await userPreferenceService.set(preferredOptionKey, currentKey as string);
        }

        setSelectedOption(new Set([currentKey as string]));
    }

    return options[selectedOptionValue] && (
        <ButtonGroup className="gap-px items-stretch">
            <Button variant="primary" className="w-52 h-auto active:scale-[0.97] transition-[scale]"
                    onPress={options[selectedOptionValue].action}>
                <div className="flex items-center gap-3 py-1 px-2 w-full">
                    {icon && (
                        <span className="shrink-0 flex items-center justify-center [&_svg]:w-full! [&_svg]:h-full!"
                              style={{width: icon.props.size ?? "1em", height: icon.props.size ?? "1em"}}>
                            {icon}
                        </span>
                    )}
                    <div className="flex flex-col items-center grow">
                        <p className="font-semibold">{options[selectedOptionValue].label}</p>
                        <p className="text-xs font-normal opacity-70 ">{description}</p>
                    </div>
                </div>
            </Button>
            {hasMultipleOptions && (
                <Dropdown>
                    <Button isIconOnly variant="primary"
                            className="h-auto self-stretch active:scale-[0.97] transition-[scale]">
                        <CaretDownIcon/>
                    </Button>
                    <Dropdown.Popover placement="bottom end">
                        <Dropdown.Menu
                            disallowEmptySelection
                            aria-label="Merge options"
                            selectedKeys={selectedOption}
                            selectionMode="single"
                            onSelectionChange={onSelectionChange}
                            className="w-60"
                        >
                            {Object.entries(options).map(([key, option]) => (
                                <Dropdown.Item key={key} id={key} textValue={option.label}
                                               className="flex flex-col items-start gap-0">
                                    <Label>{option.label}</Label>
                                    <Description>{option.description}</Description>
                                    <Dropdown.ItemIndicator/>
                                </Dropdown.Item>
                            ))}
                        </Dropdown.Menu>
                    </Dropdown.Popover>
                </Dropdown>
            )}
        </ButtonGroup>
    );
}