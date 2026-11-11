import GameSearchResults from "./GameSearchResults";
import ResponsiveDialog from "Frontend/components/layout/ResponsiveDialog";
import {Button, Input, Modal, Spinner, Tooltip} from "@heroui/react";
import React, {useEffect, useState} from "react";
import {ArrowRightIcon, MagnifyingGlassIcon} from "@phosphor-icons/react";
import {GameEndpoint} from "Frontend/generated/endpoints";
import GameSearchResultDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSearchResultDto";
import {useSnapshot} from "valtio/react";
import {libraryState} from "Frontend/state/LibraryState";
import LibraryAdminDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryAdminDto";

interface MatchGameModalProps {
    path: string;
    libraryId: number;
    replaceGameId?: number;
    initialSearchTerm: string;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
}

export default function MatchGameModal({
                                           path,
                                           libraryId,
                                           replaceGameId,
                                           initialSearchTerm,
                                           isOpen,
                                           onOpenChange
                                       }: MatchGameModalProps) {
    const [searchTerm, setSearchTerm] = useState("");
    const [searchResults, setSearchResults] = useState<GameSearchResultDto[]>([]);
    const [isSearching, setIsSearching] = useState(false);
    const [isMatching, setIsMatching] = useState<string | null>(null);

    const librariesState = useSnapshot(libraryState).state;

    useEffect(() => {
        setSearchTerm(initialSearchTerm);
        setSearchResults([]);
    }, [isOpen]);

    async function matchGame(result: GameSearchResultDto) {
        await GameEndpoint.matchManually(result.originalIds, path, libraryId, replaceGameId);
    }

    async function search() {
        setIsSearching(true);
        const results = await GameEndpoint.getPotentialMatches(searchTerm, (librariesState[libraryId] as LibraryAdminDto).platforms);
        setSearchResults(results);
        setIsSearching(false);
    }

    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}
                            isDismissable={!isSearching && !isMatching}>
                <ResponsiveDialog size="lg" className="max-w-5xl">
                    <Modal.Dialog>
                        {({close}) => (
                            <Modal.Body className="my-4">
                                <div className="flex flex-col items-center">
                                    <pre className="max-w-full whitespace-pre-wrap wrap-anywhere">{path}</pre>
                                </div>
                                <div className="flex flex-row gap-2 mb-4">
                                    <Input aria-label="Search games" value={searchTerm}
                                           onChange={(e) => setSearchTerm(e.target.value)}
                                           onKeyDown={async (e) => {
                                               if (e.key === "Enter") {
                                                   e.preventDefault();
                                                   await search();
                                               }
                                           }}
                                           className="flex-1"
                                    />
                                    <Button isIconOnly aria-label="Search games" onPress={search} variant="primary" isPending={isSearching}>
                                        {isSearching ? <Spinner size="sm" color="current"/> : <MagnifyingGlassIcon/>}
                                    </Button>
                                </div>

                                <div>
                                    <GameSearchResults results={searchResults} actions={item => (
                                        <Tooltip>
                                            <Button isIconOnly size="sm" aria-label={`Pick ${item.title}`}
                                                    isDisabled={isMatching !== null}
                                                    isPending={isMatching === item.id}
                                                    onPress={async () => {
                                                        setIsMatching(item.id);
                                                        await matchGame(item);
                                                        setIsMatching(null);
                                                        close();
                                                    }}>
                                                {isMatching === item.id ? <Spinner size="sm" color="current"/> : <ArrowRightIcon/>}
                                            </Button>
                                            <Tooltip.Content placement="bottom">Pick this result</Tooltip.Content>
                                        </Tooltip>
                                    )}/>
                                </div>
                            </Modal.Body>
                        )}
                    </Modal.Dialog>
                </ResponsiveDialog>
            </Modal.Backdrop>
        </Modal>
    );
}