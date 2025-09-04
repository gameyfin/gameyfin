import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {Button, Image, Input, Modal, ModalBody, ModalContent, ModalHeader, ScrollShadow} from "@heroui/react";
import React, {useEffect, useState} from "react";
import GameSearchResultDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSearchResultDto";
import {GameEndpoint} from "Frontend/generated/endpoints";
import {ArrowRight, MagnifyingGlass} from "@phosphor-icons/react";
import PluginIcon from "Frontend/components/general/plugin/PluginIcon";
import {useSnapshot} from "valtio/react";
import {pluginState} from "Frontend/state/PluginState";
import PluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/PluginDto";

interface GameCoverPickerModalProps {
    game: GameDto;
    isOpen: boolean;
    onOpenChange: () => void;
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
        const results = await GameEndpoint.getPotentialMatches(searchTerm);
        let validResults = results.filter(result => result.coverUrls && result.coverUrls.length > 0);
        setSearchResults(validResults);
        setIsSearching(false);
    }

    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="opaque" size="2xl">
            <ModalContent>
                {(onClose) => {
                    return (<>
                        <ModalHeader>
                            Enter a URL or search for a cover
                        </ModalHeader>
                        <ModalBody className="flex flex-col gap-4">
                            <div className="flex flex-row gap-2 mb-4">
                                <Input isClearable
                                       placeholder="Enter a URL"
                                       value={coverUrl}
                                       onValueChange={setCoverUrlState}
                                       onClear={() => setCoverUrlState("")}
                                />
                                <Button isIconOnly onPress={() => {
                                    setCoverUrl(coverUrl);
                                    onClose();
                                }}>
                                    <ArrowRight/>
                                </Button>
                            </div>
                            <div className="flex flex-row gap-2 mb-4">
                                <Input placeholder="Search"
                                       value={searchTerm}
                                       onValueChange={setSearchTerm}
                                       onKeyDown={async (e) => {
                                           if (e.key === "Enter") {
                                               e.preventDefault();
                                               await search();
                                           }
                                       }}
                                />
                                <Button isIconOnly onPress={search} color="primary" isLoading={isSearching}>
                                    <MagnifyingGlass/>
                                </Button>
                            </div>
                            {searchResults.length === 0 && !isSearching &&
                                <p className="text-center">No results found.</p>
                            }
                            {searchResults.length === 0 && isSearching &&
                                <p className="text-center text-default-500">Searching...</p>
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
                                             onOpenChange();
                                         }}
                                    >
                                        <Image
                                            alt={cover.title}
                                            className="z-0 object-cover aspect-[12/17] group-hover:brightness-[25%]"
                                            src={cover.url}
                                            radius="none"
                                            height={216}
                                        />
                                        <div
                                            className="absolute inset-0 flex flex-col gap-4 items-center justify-center opacity-0 group-hover:opacity-100">
                                            <PluginIcon plugin={state[cover.source] as PluginDto} size={32}
                                                        blurred={false} showTooltip={false}/>
                                            <p className="text-s text-center">{cover.title}</p>
                                            <ArrowRight/>
                                        </div>
                                    </div>
                                ))}
                            </ScrollShadow>
                        </ModalBody>
                    </>)
                }}
            </ModalContent>
        </Modal>
    );
}