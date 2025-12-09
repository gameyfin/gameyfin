import {Autocomplete, AutocompleteItem} from "@heroui/react";
import {CaretRightIcon, MagnifyingGlassIcon} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import {useNavigate} from "react-router";
import {GameCover} from "Frontend/components/general/covers/GameCover";

export default function SearchBar() {

    const navigate = useNavigate();
    const state = useSnapshot(gameState);
    const games = state.games;

    return <Autocomplete
        aria-label="Search for games"
        classNames={{
            selectorButton: "text-default-500",
            endContentWrapper: "display-none"
        }}
        defaultItems={games}
        inputProps={{
            classNames: {
                input: "text-small w-96",
                inputWrapper: "h-full font-normal text-default-500 bg-default-400/20 dark:bg-default-500/20"
            },
        }}
        listboxProps={{
            hideSelectedIcon: true,
            itemClasses: {
                base: [
                    "text-default-500",
                    "transition-opacity",
                    "data-[hover=true]:text-foreground",
                    "dark:data-[hover=true]:bg-default-50",
                    "data-[pressed=true]:opacity-70",
                    "data-[hover=true]:bg-default-200",
                    "data-[selectable=true]:focus:bg-default-100",
                    "data-[focus-visible=true]:ring-default-500",
                ],
            },
        }}
        placeholder="Type to search..."
        startContent={<MagnifyingGlassIcon/>}
        isVirtualized={true}
        maxListboxHeight={300}
        itemHeight={91} // 75px (cover) + 16px (margin top/bottom) = 91px
    >
        {(item) => (
            <AutocompleteItem key={item.id} textValue={item.title} onPress={() => navigate("/game/" + item.id)}>
                <div className="flex flex-row gap-4 items-center">
                    <GameCover game={item} size={75}/>
                    <div className="flex flex-col flex-1 gap-2">
                        <p><b>{item.title}</b> ({item.release && new Date(item.release).getFullYear()})</p>
                        <p className="text-default-500">{item.developers && [...item.developers].sort().join(" / ")}</p>
                    </div>
                    <CaretRightIcon/>
                </div>
            </AutocompleteItem>
        )}
    </Autocomplete>
}