import {useEffect, useState} from "react";
import {
    Button,
    ButtonGroup,
    Dropdown,
    DropdownItem,
    DropdownMenu,
    DropdownTrigger,
    SharedSelection
} from "@heroui/react";
import {CaretDown} from "@phosphor-icons/react";
import {useUserPreferenceService} from "Frontend/util/user-preference-service";

export interface ComboButtonOption {
    label: string;
    description: string;
    action: () => void;
    isDisabled?: boolean;
}

export interface ComboButtonProps {
    description?: string;
    options: Record<string, ComboButtonOption>;
    preferredOptionKey?: string;
}

export default function ComboButton({options, preferredOptionKey, description}: ComboButtonProps) {
    const [selectedOption, setSelectedOption] = useState(new Set([Object.keys(options)[0]]));
    const selectedOptionValue = Array.from(selectedOption)[0];
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

    async function onSelectionChange(keys: SharedSelection) {
        if (!keys.currentKey) return;

        if (preferredOptionKey) {
            await userPreferenceService.set(preferredOptionKey, keys.currentKey);
        }

        setSelectedOption(new Set([keys.currentKey]));
    }

    return options[selectedOptionValue] && (
        <ButtonGroup className="gap-[1px]">
            <Button color="primary" className="w-52"
                    onPress={options[selectedOptionValue].action}>
                <div className="flex flex-col items-center">
                    <p className="font-semibold">{options[selectedOptionValue].label}</p>
                    <p className="text-xs font-normal opacity-70 ">{description}</p>
                </div>
            </Button>
            <Dropdown placement="bottom-end">
                <DropdownTrigger>
                    <Button isIconOnly color="primary">
                        <CaretDown/>
                    </Button>
                </DropdownTrigger>
                <DropdownMenu
                    disallowEmptySelection
                    aria-label="Merge options"
                    selectedKeys={selectedOption}
                    selectionMode="single"
                    onSelectionChange={onSelectionChange}
                    className="w-60"
                >
                    {Object.entries(options).map(([key, option]) => (
                        <DropdownItem key={key} description={option.description}>
                            {option.label}
                        </DropdownItem>
                    ))}
                </DropdownMenu>
            </Dropdown>
        </ButtonGroup>
    );
}