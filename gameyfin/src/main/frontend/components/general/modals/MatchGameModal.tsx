import {Button, Input, Modal, ModalBody, ModalContent} from "@heroui/react";
import React, {useEffect, useState} from "react";
import {MagnifyingGlass} from "@phosphor-icons/react";
import {GameEndpoint} from "Frontend/generated/endpoints";
import GameSearchResultDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameSearchResultDto";
import PluginIcon from "../plugin/PluginIcon";

interface EditGameMetadataModalProps {
    initialSearchTerm: string;
    isOpen: boolean;
    onOpenChange: () => void;
}

export default function MatchGameModal({initialSearchTerm, isOpen, onOpenChange}: EditGameMetadataModalProps) {
    const [searchTerm, setSearchTerm] = useState("");
    const [searchResults, setSearchResults] = useState<GameSearchResultDto[]>([]);
    const [isLoading, setIsLoading] = useState(false);

    useEffect(() => {
        setSearchTerm(initialSearchTerm);
        setSearchResults([]);
    }, [isOpen]);

    async function search() {
        setIsLoading(true);
        const results = await GameEndpoint.getPotentialMatches(searchTerm);
        setSearchResults(results);
        setIsLoading(false);
    }

    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="opaque" size="3xl" hideCloseButton>
            <ModalContent>
                <ModalBody className="my-4">
                    <div className="flex flex-row gap-2 mb-4">
                        <Input value={searchTerm} onValueChange={setSearchTerm}/>
                        <Button isIconOnly onPress={search} color="primary" isLoading={isLoading}>
                            <MagnifyingGlass/>
                        </Button>
                    </div>

                    <div className="min-h-52 mx-2">
                        {searchResults.length === 0 ?
                            <p className="text-gray-500 text-center">No results found.</p> :
                            <div className="flex flex-col gap-2">
                                {searchResults.map((result, index) => (
                                    <div className="flex flex-row items-center gap-2">
                                        <p key={index}>{result.title} ({new Date(result.release).getFullYear()})</p>
                                        {Object.keys(result.originalIds)
                                            .map(pluginId => <PluginIcon pluginId={pluginId}/>)
                                        }
                                    </div>
                                ))}
                            </div>
                        }
                    </div>
                </ModalBody>
            </ModalContent>
        </Modal>
    );
}