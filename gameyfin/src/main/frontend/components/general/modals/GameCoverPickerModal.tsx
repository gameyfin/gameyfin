import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import {Button, Image, Input, Modal, ModalBody, ModalContent, ModalHeader, ScrollShadow} from "@heroui/react";
import React, {useEffect, useState} from "react";
import GameSearchResultDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameSearchResultDto";
import {GameEndpoint} from "Frontend/generated/endpoints";
import {ArrowRight, MagnifyingGlass} from "@phosphor-icons/react";

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
    const [isSearching, setIsSearching] = useState(false)

    useEffect(() => {
        if (isOpen && searchTerm.length > 0 && searchResults.length === 0) {
            search();
        }
    }, [isOpen]);

    async function search() {
        setIsSearching(true);
        const results = await GameEndpoint.getPotentialMatches(searchTerm, false);
        let validResults = results.filter(result => result.coverUrl && result.coverUrl.length > 0 && result.coverUrl !== "null");
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
                            <ScrollShadow
                                className="flex flex-row flex-wrap gap-4 h-96 overflow-scroll justify-evenly">
                                {searchResults.length === 0 && "No results found."}
                                {searchResults.map((result) => (
                                    <Image
                                        key={result.id}
                                        alt={result.title}
                                        className="z-0 object-cover aspect-[12/17] cursor-pointer"
                                        src={result.coverUrl!}
                                        radius="none"
                                        height={216}
                                        onClick={() => {
                                            setCoverUrl(result.coverUrl!);
                                            onClose();
                                        }}
                                    />
                                ))}
                            </ScrollShadow>
                        </ModalBody>
                    </>)
                }}
            </ModalContent>
        </Modal>
    );
}