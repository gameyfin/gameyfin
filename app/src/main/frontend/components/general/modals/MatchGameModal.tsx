import {
    Button,
    Input,
    Modal,
    ModalBody,
    ModalContent,
    Table,
    TableBody,
    TableCell,
    TableColumn,
    TableHeader,
    TableRow,
    Tooltip
} from "@heroui/react";
import React, {useEffect, useState} from "react";
import {ArrowRight, MagnifyingGlass} from "@phosphor-icons/react";
import {GameEndpoint} from "Frontend/generated/endpoints";
import GameSearchResultDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSearchResultDto";
import PluginIcon from "../plugin/PluginIcon";
import {useSnapshot} from "valtio/react";
import {pluginState} from "Frontend/state/PluginState";
import PluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/PluginDto";

interface MatchGameModalProps {
    path: string;
    libraryId: number;
    replaceGameId?: number;
    initialSearchTerm: string;
    isOpen: boolean;
    onOpenChange: () => void;
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

    const state = useSnapshot(pluginState).state;

    useEffect(() => {
        setSearchTerm(initialSearchTerm);
        setSearchResults([]);
    }, [isOpen]);

    async function matchGame(result: GameSearchResultDto) {
        await GameEndpoint.matchManually(result.originalIds, path, libraryId, replaceGameId);
    }

    async function search() {
        setIsSearching(true);
        const results = await GameEndpoint.getPotentialMatches(searchTerm);
        setSearchResults(results);
        setIsSearching(false);
    }

    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange}
               hideCloseButton
               isDismissable={!isSearching && !isMatching}
               isKeyboardDismissDisabled={!isSearching && !isMatching}
               backdrop="opaque" size="5xl">
            <ModalContent>
                {(onClose) => (
                    <ModalBody className="my-4">
                        <div className="flex flex-col items-center">
                            <pre>{path}</pre>
                        </div>
                        <div className="flex flex-row gap-2 mb-4">
                            <Input value={searchTerm}
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

                        <div>
                            <Table removeWrapper isStriped isHeaderSticky
                                   classNames={{
                                       base: "h-80 overflow-y-auto",
                                   }}
                            >
                                <TableHeader>
                                    <TableColumn>Title & Release</TableColumn>
                                    <TableColumn>Developer(s)</TableColumn>
                                    <TableColumn>Publisher(s)</TableColumn>
                                    {/* width={1} keeps the column as far to the right as possible*/}
                                    <TableColumn>Sources</TableColumn>
                                    <TableColumn width={1}> </TableColumn>
                                </TableHeader>
                                <TableBody emptyContent="Your filter did not match any games." items={searchResults}>
                                    {(item) => (
                                        <TableRow key={item.id}>
                                            <TableCell>
                                                {item.title} ({item.release ? new Date(item.release).getFullYear() : "unknown"})
                                            </TableCell>
                                            <TableCell>
                                                <div className="flex flex-col">
                                                    {item.developers ? item.developers.map(
                                                        developer => <p>{developer}</p>
                                                    ) : "unknown"}
                                                </div>
                                            </TableCell>
                                            <TableCell>
                                                <div className="flex flex-col">
                                                    {item.publishers ? item.publishers.map(
                                                        publisher => <p>{publisher}</p>
                                                    ) : "unknown"}
                                                </div>
                                            </TableCell>
                                            <TableCell>
                                                <div className="flex flex-row gap-2">
                                                    {Object.values(item.originalIds).map(
                                                        originalId => <PluginIcon
                                                            plugin={state[originalId.pluginId] as PluginDto}/>
                                                    )}
                                                </div>
                                            </TableCell>
                                            <TableCell>
                                                <Tooltip content="Pick this result">
                                                    <Button isIconOnly size="sm"
                                                            isDisabled={isMatching !== null}
                                                            isLoading={isMatching === item.id}
                                                            onPress={async () => {
                                                                setIsMatching(item.id);
                                                                await matchGame(item);
                                                                setIsMatching(null);
                                                                onClose();
                                                            }}>
                                                        <ArrowRight/>
                                                    </Button>
                                                </Tooltip>
                                            </TableCell>
                                        </TableRow>
                                    )}
                                </TableBody>
                            </Table>
                        </div>
                    </ModalBody>
                )}
            </ModalContent>
        </Modal>
    );
}