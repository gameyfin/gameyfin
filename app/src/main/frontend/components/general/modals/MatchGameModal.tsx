import {
    Button,
    Input,
    Modal,
    Table,
    Tooltip
} from "@heroui/react";
import React, {useEffect, useState} from "react";
import {ArrowRightIcon, MagnifyingGlassIcon} from "@phosphor-icons/react";
import {GameEndpoint} from "Frontend/generated/endpoints";
import GameSearchResultDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSearchResultDto";
import PluginIcon from "../plugin/PluginIcon";
import {useSnapshot} from "valtio/react";
import {pluginState} from "Frontend/state/PluginState";
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

    const state = useSnapshot(pluginState).state;
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
                <Modal.Container size="lg" className="max-w-5xl">
                    <Modal.Dialog>
                        {({close}) => (
                            <Modal.Body className="my-4">
                                <div className="flex flex-col items-center">
                                    <pre>{path}</pre>
                                </div>
                                <div className="flex flex-row gap-2 mb-4">
                                    <Input value={searchTerm}
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

                                <div>
                                    <Table className="h-80">
                                        <Table.ScrollContainer className="h-80">
                                            <Table.Content>
                                                <Table.Header>
                                                    <Table.Column id="title">Title & Release</Table.Column>
                                                    <Table.Column id="developers">Developer(s)</Table.Column>
                                                    <Table.Column id="publishers">Publisher(s)</Table.Column>
                                                    <Table.Column id="sources">Sources</Table.Column>
                                                    <Table.Column id="actions"> </Table.Column>
                                                </Table.Header>
                                                <Table.Body renderEmptyState={() => <p className="text-center text-muted p-4">Your filter did not match any games.</p>}
                                                            items={searchResults}>
                                                    {(item) => (
                                                        <Table.Row key={item.id}>
                                                            <Table.Cell>
                                                                {item.title} ({item.release ? new Date(item.release).getFullYear() : "unknown"})
                                                            </Table.Cell>
                                                            <Table.Cell>
                                                                <div className="flex flex-col">
                                                                    {item.developers ? item.developers.map(
                                                                        developer => <p>{developer}</p>
                                                                    ) : "unknown"}
                                                                </div>
                                                            </Table.Cell>
                                                            <Table.Cell>
                                                                <div className="flex flex-col">
                                                                    {item.publishers ? item.publishers.map(
                                                                        publisher => <p>{publisher}</p>
                                                                    ) : "unknown"}
                                                                </div>
                                                            </Table.Cell>
                                                            <Table.Cell>
                                                                <div className="flex flex-row gap-2">
                                                                    {Object.values(item.originalIds).map(
                                                                        originalId => <PluginIcon
                                                                            plugin={state[originalId.pluginId]}/>
                                                                    )}
                                                                </div>
                                                            </Table.Cell>
                                                            <Table.Cell>
                                                                <Tooltip>
                                                                    <Tooltip.Trigger>
                                                                        <Button isIconOnly size="sm"
                                                                                isDisabled={isMatching !== null}
                                                                                isPending={isMatching === item.id}
                                                                                onPress={async () => {
                                                                                    setIsMatching(item.id);
                                                                                    await matchGame(item);
                                                                                    setIsMatching(null);
                                                                                    close();
                                                                                }}>
                                                                            <ArrowRightIcon/>
                                                                        </Button>
                                                                    </Tooltip.Trigger>
                                                                    <Tooltip.Content placement="bottom">Pick this
                                                                        result</Tooltip.Content>
                                                                </Tooltip>
                                                            </Table.Cell>
                                                        </Table.Row>
                                                    )}
                                                </Table.Body>
                                            </Table.Content>
                                        </Table.ScrollContainer>
                                    </Table>
                                </div>
                            </Modal.Body>
                        )}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}