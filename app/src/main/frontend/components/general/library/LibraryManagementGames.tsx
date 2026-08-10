import LibraryDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryDto";
import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {
    Button,
    Input,
    Link,
    ListBox,
    Select,
    SortDescriptor,
    Table,
    Tooltip,
    useOverlayState
} from "@heroui/react";
import {CheckCircleIcon, MagnifyingGlassIcon, PencilIcon, TrashIcon} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import {GameEndpoint} from "Frontend/generated/endpoints";
import GameUpdateDto from "Frontend/generated/org/gameyfin/app/games/dto/GameUpdateDto";
import {useMemo, useState} from "react";
import EditGameMetadataModal from "Frontend/components/general/modals/EditGameMetadataModal";
import MatchGameModal from "Frontend/components/general/modals/MatchGameModal";
import {GameAdminDto} from "Frontend/dtos/GameDtos";
import MetadataCompletenessIndicator from "Frontend/components/general/MetadataCompletenessIndicator";
import {metadataCompleteness} from "Frontend/util/utils";
import ChipList from "Frontend/components/general/ChipList";
import SimplePagination from "Frontend/components/general/SimplePagination";

interface LibraryManagementGamesProps {
    library: LibraryDto;
}

export default function LibraryManagementGames({library}: LibraryManagementGamesProps) {
    const rowsPerPage = 25;

    const state = useSnapshot(gameState);
    const games = state.gamesByLibraryId[library.id] ? state.gamesByLibraryId[library.id] : [];
    const [searchTerm, setSearchTerm] = useState("");
    const [filter, setFilter] = useState<"all" | "confirmed" | "nonConfirmed">("all");
    const [sortDescriptor, setSortDescriptor] = useState<SortDescriptor>({column: "title", direction: "ascending"});

    const [selectedGame, setSelectedGame] = useState<GameAdminDto>(games[0] as GameAdminDto);
    const editGameModal = useOverlayState();
    const matchGameModal = useOverlayState();

    const [page, setPage] = useState(1);
    const pages = useMemo(() => {
        return Math.ceil(getFilteredGames().length / rowsPerPage);
    }, [games, filter]);

    const filteredItems = useMemo(() => {
        return getFilteredGames();
    }, [games, filter, searchTerm]);

    const sortedItems = useMemo(() => {
        return (filteredItems as GameAdminDto[]).slice().sort((a, b) => {
            let cmp: number;

            switch (sortDescriptor.column) {
                case "title":
                    cmp = a.title.localeCompare(b.title);
                    break;
                case "addedToLibrary":
                    cmp = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
                    break;
                case "downloadCount":
                    cmp = a.metadata.downloadCount - b.metadata.downloadCount;
                    break;
                case "completeness":
                    cmp = metadataCompleteness(a) - metadataCompleteness(b);
                    break;
                default:
                    return 0; // No sorting if the column is not recognized
            }

            if (sortDescriptor.direction === "descending") {
                cmp *= -1; // Reverse the comparison if sorting in descending order
            }

            return cmp;
        });
    }, [filteredItems, sortDescriptor]);

    const pagedItems = useMemo(() => {
        const start = (page - 1) * rowsPerPage;
        const end = start + rowsPerPage;
        return sortedItems.slice(start, end);
    }, [page, sortedItems]);


    function getFilteredGames() {
        let filteredGames = (games as GameAdminDto[]).filter((game) =>
            game.metadata.path!.toLowerCase().includes(searchTerm.toLowerCase()) ||
            game.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
            game.publishers?.some(publisher => publisher.toLowerCase().includes(searchTerm.toLowerCase())) ||
            game.developers?.some(developer => developer.toLowerCase().includes(searchTerm.toLowerCase()))
        )

        if (filter === "confirmed") {
            return filteredGames.filter(g => g.metadata.matchConfirmed);
        } else if (filter === "nonConfirmed") {
            return filteredGames.filter(g => !g.metadata.matchConfirmed);
        }

        return filteredGames;
    }

    async function toggleMatchConfirmed(game: GameAdminDto) {
        await GameEndpoint.updateGame(
            {
                id: game.id,
                metadata: {matchConfirmed: !game.metadata.matchConfirmed}
            } as GameUpdateDto
        )
    }

    async function deleteGame(game: GameDto) {
        await GameEndpoint.deleteGame(game.id);
    }

    return selectedGame && <div className="flex flex-col gap-4">
        <h1 className="text-2xl font-bold">Manage games in library</h1>
        <div className="flex flex-row gap-2 justify-between">
            <Input
                className="w-96"
                placeholder="Search"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
            />
            <Select value={filter}
                    onChange={(value) => setFilter(value as "all" | "confirmed" | "nonConfirmed")}
                    className="w-64">
                <Select.Trigger>
                    <Select.Value/>
                    <Select.Indicator/>
                </Select.Trigger>
                <Select.Popover>
                    <ListBox>
                        <ListBox.Item id="all" textValue="Show all">
                            Show all
                            <ListBox.ItemIndicator/>
                        </ListBox.Item>
                        <ListBox.Item id="confirmed" textValue="Show only confirmed">
                            Show only confirmed
                            <ListBox.ItemIndicator/>
                        </ListBox.Item>
                        <ListBox.Item id="nonConfirmed" textValue="Show only non confirmed">
                            Show only non confirmed
                            <ListBox.ItemIndicator/>
                        </ListBox.Item>
                    </ListBox>
                </Select.Popover>
            </Select>
        </div>
        <Table className="h-[35rem]">
            <Table.ScrollContainer className="h-[35rem]">
                <Table.Content sortDescriptor={sortDescriptor} onSortChange={setSortDescriptor}>
                    <Table.Header>
                        <Table.Column id="title" allowsSorting>Game</Table.Column>
                        <Table.Column id="platforms">Platforms</Table.Column>
                        <Table.Column id="addedToLibrary" allowsSorting>Added to library</Table.Column>
                        <Table.Column id="downloadCount" allowsSorting>Download count</Table.Column>
                        <Table.Column id="path">Path</Table.Column>
                        <Table.Column id="completeness" allowsSorting>Completeness</Table.Column>
                        <Table.Column id="actions">Actions</Table.Column>
                    </Table.Header>
                    <Table.Body renderEmptyState={() => <p className="text-center text-muted p-4">Your filter did not match any games.</p>} items={pagedItems}>
                        {(item: GameAdminDto) => (
                            <Table.Row key={item.id}>
                                <Table.Cell>
                                    <Link href={`/game/${item.id}`}
                                          className="text-sm text-foreground hover:underline">
                                        {item.title} ({item.release ? new Date(item.release).getFullYear() : "unknown"})
                                    </Link>
                                </Table.Cell>
                                <Table.Cell>
                                    <ChipList items={item.platforms} maxVisible={1} defaultContent="Unspecified"/>
                                </Table.Cell>
                                <Table.Cell>
                                    {new Date(item.createdAt).toLocaleString()}
                                </Table.Cell>
                                <Table.Cell>
                                    {item.metadata.downloadCount}
                                </Table.Cell>
                                <Table.Cell>
                                    {item.metadata.path}
                                </Table.Cell>
                                <Table.Cell>
                                    <MetadataCompletenessIndicator game={item}/>
                                </Table.Cell>
                                <Table.Cell>
                                    <div className="flex flex-row gap-2">
                                        <Tooltip>
                                            <Tooltip.Trigger>
                                                <Button isIconOnly size="sm" onPress={() => toggleMatchConfirmed(item)}>
                                                    {item.metadata.matchConfirmed ?
                                                        <CheckCircleIcon weight="fill" className="fill-success"/> :
                                                        <CheckCircleIcon/>}
                                                </Button>
                                            </Tooltip.Trigger>
                                            <Tooltip.Content>
                                                {item.metadata.matchConfirmed ? "Unconfirm match" : "Confirm match"}
                                            </Tooltip.Content>
                                        </Tooltip>
                                        <Tooltip>
                                            <Tooltip.Trigger>
                                                <Button isIconOnly size="sm" onPress={() => {
                                                    setSelectedGame(item);
                                                    editGameModal.open();
                                                }}>
                                                    <PencilIcon/>
                                                </Button>
                                            </Tooltip.Trigger>
                                            <Tooltip.Content>Edit metadata</Tooltip.Content>
                                        </Tooltip>
                                        <Tooltip>
                                            <Tooltip.Trigger>
                                                <Button isIconOnly size="sm" onPress={() => {
                                                    setSelectedGame(item);
                                                    matchGameModal.open();
                                                }}>
                                                    <MagnifyingGlassIcon/>
                                                </Button>
                                            </Tooltip.Trigger>
                                            <Tooltip.Content>Match game</Tooltip.Content>
                                        </Tooltip>
                                        <Tooltip>
                                            <Tooltip.Trigger>
                                                <Button isIconOnly size="sm" variant="danger"
                                                        onPress={() => deleteGame(item)}>
                                                    <TrashIcon/>
                                                </Button>
                                            </Tooltip.Trigger>
                                            <Tooltip.Content>Remove from library</Tooltip.Content>
                                        </Tooltip>
                                    </div>
                                </Table.Cell>
                            </Table.Row>
                        )}
                    </Table.Body>
                </Table.Content>
            </Table.ScrollContainer>
        </Table>
        {pagedItems.length > 0 &&
            <div className="flex w-full justify-center sticky">
                <SimplePagination page={page} total={pages} onChange={setPage}/>
            </div>
        }
        <EditGameMetadataModal game={selectedGame}
                               isOpen={editGameModal.isOpen}
                               onOpenChange={editGameModal.setOpen}/>
        <MatchGameModal path={selectedGame.metadata.path!}
                        libraryId={library.id}
                        replaceGameId={selectedGame.id}
                        initialSearchTerm={selectedGame.title}
                        isOpen={matchGameModal.isOpen}
                        onOpenChange={matchGameModal.setOpen}/>
    </div>;
}