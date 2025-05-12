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
import {UserPreferenceService} from "Frontend/util/user-preference-service";

export interface ComboButtonOption {
    label: string;
    description: string;
    action: () => void;
    isDisabled?: boolean;
}

export interface ComboButtonProps {
    options: Record<string, ComboButtonOption>;
    preferredOptionKey?: string;
}

export default function ComboButton({options, preferredOptionKey}: ComboButtonProps) {
    const [selectedOption, setSelectedOption] = useState(new Set([Object.keys(options)[0]]));
    const [disabledOptions] = useState<string[]>(getDisabledKeys(options));
    const selectedOptionValue = Array.from(selectedOption)[0];

    useEffect(() => {
        if (!preferredOptionKey) return;

        UserPreferenceService.get(preferredOptionKey).then((key) => {
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
            await UserPreferenceService.set(preferredOptionKey, keys.currentKey);
        }

        setSelectedOption(new Set([keys.currentKey]));
    }

    function getDisabledKeys(options: Record<string, ComboButtonOption>): string[] {
        return Object.keys(options).filter(key => options[key].isDisabled);
    }

    return (
        <ButtonGroup className="gap-[1px]">
            <Button color="primary" className="font-semibold w-52"
                    onPress={options[selectedOptionValue].action}>{options[selectedOptionValue].label}
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
                    disabledKeys={disabledOptions}
                    selectionMode="single"
                    /*@ts-ignore*/
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