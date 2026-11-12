import {ComboBox, ListBox, SearchField, useFilter} from "@heroui/react";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import {useNavigate} from "react-router";
import {useRef, useState} from "react";
import type {Key} from "react-aria-components";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import GameSummaryDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSummaryDto";

export default function SearchBar({inputValue, onInputChange}: {
    inputValue: string;
    onInputChange: (value: string) => void
}) {

    const navigate = useNavigate();
    const state = useSnapshot(gameState);
    const games = state.games;

    const [selectedKey, setSelectedKey] = useState<number | null>(null);
    const inputRef = useRef<HTMLInputElement>(null);
    const {contains} = useFilter({sensitivity: "base"});
    const filteredGames = games.filter(game => contains(game.title, inputValue));

    const handleSelectionChange = (key: Key | null) => {
        if (key == null) return;
        navigate("/game/" + key);
        setSelectedKey(null);
        onInputChange("");
        inputRef.current?.blur();
    };

    return <ComboBox
        className="w-full min-w-0 xl:w-96 xl:shrink-0"
        aria-label="Search for games"
        items={filteredGames}
        allowsCustomValue={false}
        value={selectedKey}
        onChange={handleSelectionChange}
        inputValue={inputValue}
        onInputChange={onInputChange}
    >
        <SearchField aria-label="Search for games" className="w-full xl:w-96">
            <SearchField.Group>
                <SearchField.SearchIcon/>
                <SearchField.Input ref={inputRef} placeholder="Type to search..."/>
                <SearchField.ClearButton/>
            </SearchField.Group>
        </SearchField>
        <ComboBox.Popover className="w-(--trigger-width) max-w-[calc(100vw-2rem)] xl:w-96">
            <ListBox className="max-h-[min(66dvh,var(--visual-viewport-height,66dvh))] overflow-y-auto">
                {(item: GameSummaryDto) => (
                    <ListBox.Item key={item.id} id={item.id} textValue={item.title}>
                        <div className="flex flex-row gap-4 items-center">
                            <GameCover game={item} size={75} showTitle={false}/>
                            <div className="flex min-w-0 flex-col flex-1 gap-2 wrap-anywhere">
                                <p>
                                    <b>{item.title} </b>
                                    {item.release && <>({new Date(item.release).getFullYear()})</>}
                                </p>
                                <p className="text-muted">{item.developers && [...item.developers].sort().join(" / ")}</p>
                            </div>
                        </div>
                    </ListBox.Item>
                )}
            </ListBox>
        </ComboBox.Popover>
    </ComboBox>
}
