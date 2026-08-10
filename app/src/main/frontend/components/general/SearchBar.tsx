import {ComboBox, Input, ListBox} from "@heroui/react";
import {MagnifyingGlassIcon} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import {useNavigate} from "react-router";
import {GameCover} from "Frontend/components/general/covers/GameCover";
import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";

export default function SearchBar() {

    const navigate = useNavigate();
    const state = useSnapshot(gameState);
    const games = state.games;

    return <ComboBox
        aria-label="Search for games"
        items={games}
        allowsCustomValue={false}
        onSelectionChange={(id) => id && navigate("/game/" + id)}
    >
        <ComboBox.InputGroup>
            <MagnifyingGlassIcon className="mx-2 text-muted"/>
            <Input placeholder="Type to search..." className="w-96"/>
        </ComboBox.InputGroup>
        <ComboBox.Popover>
            <ListBox>
                {(item: GameDto) => (
                    <ListBox.Item key={item.id} id={item.id} textValue={item.title}>
                        <div className="flex flex-row gap-4 items-center">
                            <GameCover game={item} size={75}/>
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