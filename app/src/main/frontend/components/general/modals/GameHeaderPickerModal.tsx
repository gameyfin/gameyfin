import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {Button, Input, Modal, ScrollShadow} from "@heroui/react";
import React, {useEffect, useState} from "react";
import GameSearchResultDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSearchResultDto";
import {GameEndpoint} from "Frontend/generated/endpoints";
import {ArrowRightIcon, MagnifyingGlassIcon, XIcon} from "@phosphor-icons/react";
import PluginIcon from "Frontend/components/general/plugin/PluginIcon";
import {useSnapshot} from "valtio/react";
import {pluginState} from "Frontend/state/PluginState";

interface GameHeaderPickerModalProps {
    game: GameDto;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    setHeaderUrl: (url: string) => void;
}

export function GameHeaderPickerModal({game, isOpen, onOpenChange, setHeaderUrl}: GameHeaderPickerModalProps) {
    const [headerUrl, setHeaderUrlState] = useState("");

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
        let validResults = results.filter(result => result.headerUrls && result.headerUrls.length > 0);
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
                                <Modal.Heading>Enter a URL or search for a header</Modal.Heading>
                            </Modal.Header>
                            <Modal.Body className="flex flex-col gap-4">
                                <div className="flex flex-row gap-2 mb-4">
                                    <div className="relative flex-1">
                                        <Input placeholder="Enter a URL"
                                               className="w-full"
                                               value={headerUrl}
                                               onChange={(e) => setHeaderUrlState(e.target.value)}
                                        />
                                        {headerUrl.length > 0 && (
                                            <button type="button"
                                                    className="absolute right-2 top-1/2 -translate-y-1/2"
                                                    onClick={() => setHeaderUrlState("")}>
                                                <XIcon/>
                                            </button>
                                        )}
                                    </div>
                                    <Button isIconOnly onPress={() => {
                                        setHeaderUrl(headerUrl);
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
                                    className="flex flex-col items-center gap-4 h-96 overflow-y-scroll">
                                    {searchResults.flatMap(result => {
                                        if (!result.headerUrls) return [];
                                        return result.headerUrls.map((url, idx) => ({
                                            id: `${result.id}-${idx}`,
                                            title: result.title,
                                            url: url.url,
                                            source: url.pluginId
                                        }))
                                    }).map(header => (
                                        <div key={header.id}
                                             className="relative group w-fit h-fit cursor-pointer"
                                             onClick={() => {
                                                 setHeaderUrl(header.url);
                                                 onOpenChange(false);
                                             }}
                                        >
                                            <img
                                                alt={header.title}
                                                className="z-0 object-cover rounded-none group-hover:brightness-25"
                                                src={header.url}
                                            />
                                            <div
                                                className="absolute inset-0 flex flex-col gap-4 items-center justify-center opacity-0 group-hover:opacity-100">
                                                <PluginIcon plugin={state[header.source]} size={32}
                                                            blurred={false} showTooltip={false}/>
                                                <p className="text-s text-center">{header.title}</p>
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