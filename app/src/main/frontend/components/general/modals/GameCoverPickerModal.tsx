import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {Button, Input, Modal, ScrollShadow} from "@heroui/react";
import React, {useEffect, useState} from "react";
import GameSearchResultDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSearchResultDto";
import {GameEndpoint} from "Frontend/generated/endpoints";
import {ArrowRightIcon, MagnifyingGlassIcon, XIcon} from "@phosphor-icons/react";
import PluginIcon from "Frontend/components/general/plugin/PluginIcon";
import {useSnapshot} from "valtio/react";
import {pluginState} from "Frontend/state/PluginState";

interface GameCoverPickerModalProps {
    game: GameDto;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    setCoverUrl: (url: string) => void;
}

export function GameCoverPickerModal({game, isOpen, onOpenChange, setCoverUrl}: GameCoverPickerModalProps) {
    const [coverUrl, setCoverUrlState] = useState("");

    const [searchTerm, setSearchTerm] = useState(game.title);
    const [searchResults, setSearchResults] = useState<GameSearchResultDto[]>([]);
    const [isSearching, setIsSearching] = useState(false);

    const state = useSnapshot(pluginState).state;

    useEffect(() => {
        if (isOpen && searchTerm.length > 0 && searchResults.length === 0) {
            search();
        }
    }, [isOpen]);

    async function search() {
        setIsSearching(true);
        const results = await GameEndpoint.getPotentialMatches(searchTerm, game.platforms);
        let validResults = results.filter(result => result.coverUrls && result.coverUrls.length > 0);
        setSearchResults(validResults);
        setIsSearching(false);
    }

    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
                <Modal.Container size="lg" className="max-w-2xl">
                    <Modal.Dialog>
                        {({close}) => (<>
                            <Modal.Header>
                                <Modal.Heading>Enter a URL or search for a cover</Modal.Heading>
                            </Modal.Header>
                            <Modal.Body className="flex flex-col gap-4">
                                <div className="flex flex-row gap-2 mb-4">
                                    <div className="relative flex-1">
                                        <Input placeholder="Enter a URL"
                                               className="w-full"
                                               value={coverUrl}
                                               onChange={(e) => setCoverUrlState(e.target.value)}
                                        />
                                        {coverUrl.length > 0 && (
                                            <button type="button"
                                                    className="absolute right-2 top-1/2 -translate-y-1/2"
                                                    onClick={() => setCoverUrlState("")}>
                                                <XIcon/>
                                            </button>
                                        )}
                                    </div>
                                    <Button isIconOnly onPress={() => {
                                        setCoverUrl(coverUrl);
                                        close();
                                    }}>
                                        <ArrowRightIcon/>
                                    </Button>
                                </div>
                                <div className="flex flex-row gap-2 mb-4">
                                    <Input placeholder="Search"
                                           className="flex-1"
                                           value={searchTerm}
                                           onChange={(e) => setSearchTerm(e.target.value)}
                                           onKeyDown={async (e) => {
                                               if (e.key === "Enter") {
                                                   e.preventDefault();
                                                   await search();
                                               }
                                           }}
                                    />
                                    <Button isIconOnly onPress={search} variant="primary" isPending={isSearching}>
                                        <MagnifyingGlassIcon/>
                                    </Button>
                                </div>
                                {searchResults.length === 0 && !isSearching &&
                                    <p className="text-center">No results found.</p>
                                }
                                {searchResults.length === 0 && isSearching &&
                                    <p className="text-center text-muted">Searching...</p>
                                }
                                <ScrollShadow
                                    className="grid grid-cols-auto-fill gap-4 h-96 overflow-y-scroll justify-evenly">
                                    {searchResults.flatMap(result => {
                                        if (!result.coverUrls) return [];
                                        return result.coverUrls.map((url, idx) => ({
                                            id: `${result.id}-${idx}`,
                                            title: result.title,
                                            url: url.url,
                                            source: url.pluginId
                                        }))
                                    }).map(cover => (
                                        <div key={cover.id}
                                             className="relative group w-fit h-fit cursor-pointer"
                                             onClick={() => {
                                                 setCoverUrl(cover.url);
                                                 onOpenChange(false);
                                             }}
                                        >
                                            <img
                                                alt={cover.title}
                                                className="z-0 object-cover aspect-12/17 rounded-none group-hover:brightness-25"
                                                src={cover.url}
                                                height={216}
                                            />
                                            <div
                                                className="absolute inset-0 flex flex-col gap-4 items-center justify-center opacity-0 group-hover:opacity-100">
                                                <PluginIcon plugin={state[cover.source]} size={32}
                                                            blurred={false} showTooltip={false}/>
                                                <p className="text-s text-center">{cover.title}</p>
                                                <ArrowRightIcon/>
                                            </div>
                                        </div>
                                    ))}
                                </ScrollShadow>
                            </Modal.Body>
                        </>)}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}