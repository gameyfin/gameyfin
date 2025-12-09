import LibraryDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryDto";
import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {
    Button,
    Input,
    Link,
    Pagination,
    Select,
    SelectItem,
    SortDescriptor,
    Table,
    TableBody,
    TableCell,
    TableColumn,
    TableHeader,
    TableRow,
    Tooltip,
    useDisclosure
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
    const editGameModal = useDisclosure();
    const matchGameModal = useDisclosure();

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
                isClearable
                placeholder="Search"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                onClear={() => setSearchTerm("")}
            />
            <Select
                selectedKeys={[filter]}
                disallowEmptySelection
                onSelectionChange={keys => setFilter(Array.from(keys)[0] as any)}
                className="w-64"
            >
                <SelectItem key="all">Show all</SelectItem>
                <SelectItem key="confirmed">Show only confirmed</SelectItem>
                <SelectItem key="nonConfirmed">Show only non confirmed</SelectItem>
            </Select>
        </div>
        <Table removeWrapper isStriped
               sortDescriptor={sortDescriptor}
               onSortChange={setSortDescriptor}
               bottomContent={
                   <div className="flex w-full justify-center sticky">
                       {pagedItems.length > 0 &&
                           <Pagination
                               isCompact
                               showControls
                               showShadow
                               color="primary"
                               page={page}
                               total={pages}
                               onChange={(page) => setPage(page)}
                           />}
                   </div>
               }>
            <TableHeader>
                <TableColumn key="title" allowsSorting>Game</TableColumn>
                <TableColumn key="platforms">Platforms</TableColumn>
                <TableColumn key="addedToLibrary" allowsSorting>Added to library</TableColumn>
                <TableColumn key="downloadCount" allowsSorting>Download count</TableColumn>
                <TableColumn>Path</TableColumn>
                <TableColumn key="completeness" allowsSorting>Completeness</TableColumn>
                {/* width={1} keeps the column as far to the right as possible*/}
                <TableColumn width={1}>Actions</TableColumn>
            </TableHeader>
            <TableBody emptyContent="Your filter did not match any games." items={pagedItems}>
                {(item: GameAdminDto) => (
                    <TableRow key={item.id}>
                        <TableCell>
                            <Link href={`/game/${item.id}`}
                                  color="foreground"
                                  className="text-sm"
                                  underline="hover">
                                {item.title} ({item.release ? new Date(item.release).getFullYear() : "unknown"})
                            </Link>
                        </TableCell>
                        <TableCell>
                            <ChipList items={item.platforms} maxVisible={1} defaultContent="Unspecified"/>
                        </TableCell>
                        <TableCell>
                            {new Date(item.createdAt).toLocaleString()}
                        </TableCell>
                        <TableCell>
                            {item.metadata.downloadCount}
                        </TableCell>
                        <TableCell>
                            {item.metadata.path}
                        </TableCell>
                        <TableCell>
                            <MetadataCompletenessIndicator game={item}/>
                        </TableCell>
                        <TableCell>
                            <div className="flex flex-row gap-2">
                                <Button isIconOnly size="sm" onPress={() => toggleMatchConfirmed(item)}>
                                    {item.metadata.matchConfirmed ?
                                        <Tooltip content="Unconfirm match">
                                            <CheckCircleIcon weight="fill" className="fill-success"/>
                                        </Tooltip> :
                                        <Tooltip content="Confirm match">
                                            <CheckCircleIcon/>
                                        </Tooltip>}
                                </Button>
                                <Button isIconOnly size="sm" onPress={() => {
                                    setSelectedGame(item);
                                    editGameModal.onOpenChange();
                                }}>
                                    <Tooltip content="Edit metadata">
                                        <PencilIcon/>
                                    </Tooltip>
                                </Button>
                                <Button isIconOnly size="sm" onPress={() => {
                                    setSelectedGame(item);
                                    matchGameModal.onOpenChange();
                                }}>
                                    <Tooltip content="Match game">
                                        <MagnifyingGlassIcon/>
                                    </Tooltip>
                                </Button>
                                <Button isIconOnly size="sm" color="danger"
                                        onPress={() => deleteGame(item)}>
                                    <Tooltip content="Remove from library">
                                        <TrashIcon/>
                                    </Tooltip>
                                </Button>
                            </div>
                        </TableCell>
                    </TableRow>
                )}
            </TableBody>
        </Table>
        <EditGameMetadataModal game={selectedGame}
                               isOpen={editGameModal.isOpen}
                               onOpenChange={editGameModal.onOpenChange}/>
        <MatchGameModal path={selectedGame.metadata.path!}
                        libraryId={library.id}
                        replaceGameId={selectedGame.id}
                        initialSearchTerm={selectedGame.title}
                        isOpen={matchGameModal.isOpen}
                        onOpenChange={matchGameModal.onOpenChange}/>
    </div>;
}