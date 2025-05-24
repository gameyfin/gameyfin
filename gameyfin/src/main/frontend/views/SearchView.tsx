import {Input} from "@heroui/react";
import {MagnifyingGlass} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import {libraryState} from "Frontend/state/LibraryState";
import {useSearchParams} from "react-router";
import {ChangeEvent} from "react";

export default function SearchView() {
    const gamesState = useSnapshot(gameState);
    const librariesState = useSnapshot(libraryState);
    const [searchParams, setSearchParams] = useSearchParams();
    const term = searchParams.get("term") ?? "";

    const updateSearchParam = (e: ChangeEvent<HTMLInputElement>) => {
        const value = e.target.value;
        if (value) {
            setSearchParams({term: value});
        } else {
            setSearchParams({});
        }
    };

    return <div className="flex flex-col items-center">
        <Input
            classNames={{
                base: "w-1/3",
                mainWrapper: "h-full",
                inputWrapper:
                    "h-full font-normal text-default-500 bg-default-400/20 dark:bg-default-500/20",
            }}
            placeholder="Type to search..."
            startContent={<MagnifyingGlass/>}
            type="search"
            value={term}
            onChange={updateSearchParam}
        />
    </div>
}