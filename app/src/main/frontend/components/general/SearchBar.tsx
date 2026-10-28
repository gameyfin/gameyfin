import {ComboBox, ListBox, SearchField} from "@heroui/react";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import {useNavigate} from "react-router";
import {useState} from "react";
import type {Key} from "react-aria-components";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import GameSummaryDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSummaryDto";

export default function SearchBar() {

    const navigate = useNavigate();
    const state = useSnapshot(gameState);
    const games = state.games;

    const [selectedKey, setSelectedKey] = useState<number | null>(null);
    const [inputValue, setInputValue] = useState("");

    const handleSelectionChange = (key: Key | null) => {
        if (key == null) return;
        navigate("/game/" + key);
        setSelectedKey(null);
        setInputValue("");
    };

    return <ComboBox
        aria-label="Search for games"
        defaultItems={games}
        allowsCustomValue={false}
        value={selectedKey}
        onChange={handleSelectionChange}
        inputValue={inputValue}
        onInputChange={setInputValue}
    >
        <SearchField aria-label="Search for games" className="w-96">
            <SearchField.Group>
                <SearchField.SearchIcon/>
                <SearchField.Input placeholder="Type to search..."/>
                <SearchField.ClearButton/>
            </SearchField.Group>
        </SearchField>
        <ComboBox.Popover className="w-96">
            <ListBox className="max-h-[66vh] overflow-y-auto">
                {(item: GameSummaryDto) => (
                    <ListBox.Item key={item.id} id={item.id} textValue={item.title}>
                        <div className="flex flex-row gap-4 items-center">
                            <GameCover game={item} size={75} showTitle={false}/>
                            <div className="flex flex-col flex-1 gap-2">
                                <p><b>{item.title}</b> ({item.release && new Date(item.release).getFullYear()})</p>
                                <p className="text-muted">{item.developers && [...item.developers].sort().join(" / ")}</p>
                            </div>
                        </div>
                    </ListBox.Item>
                )}
            </ListBox>
        </ComboBox.Popover>
    </ComboBox>
}