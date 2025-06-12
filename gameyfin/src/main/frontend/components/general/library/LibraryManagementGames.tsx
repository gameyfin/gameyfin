import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryDto";
import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import {
    Button,
    Link,
    Pagination,
    Select,
    SelectItem,
    Table,
    TableBody,
    TableCell,
    TableColumn,
    TableHeader,
    TableRow,
    Tooltip,
    useDisclosure
} from "@heroui/react";
import {CheckCircle, MagnifyingGlass, Pencil, Trash} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import {GameEndpoint} from "Frontend/generated/endpoints";
import GameUpdateDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameUpdateDto";
import {useMemo, useState} from "react";
import EditGameMetadataModal from "Frontend/components/general/modals/EditGameMetadataModal";
import MatchGameModal from "Frontend/components/general/modals/MatchGameModal";

interface LibraryManagementGamesProps {
    library: LibraryDto;
}

export default function LibraryManagementGames({library}: LibraryManagementGamesProps) {
    const rowsPerPage = 25;

    const state = useSnapshot(gameState);
    const games = state.gamesByLibraryId[library.id] ? state.gamesByLibraryId[library.id] as GameDto[] : [];
    const [filter, setFilter] = useState<"all" | "confirmed" | "nonConfirmed">("all");

    const [selectedGame, setSelectedGame] = useState<GameDto>(games[0]);
    const editGameModal = useDisclosure();
    const matchGameModal = useDisclosure();

    const [page, setPage] = useState(1);
    const pages = useMemo(() => {
        return Math.ceil(getFilteredGames().length / rowsPerPage);
    }, [games, filter]);

    const items = useMemo(() => {
        const start = (page - 1) * rowsPerPage;
        const end = start + rowsPerPage;

        return getFilteredGames().slice(start, end);
    }, [page, games, filter]);


    function getFilteredGames() {
        if (filter === "confirmed") {
            return games.filter(g => g.metadata.matchConfirmed);
        }
        if (filter === "nonConfirmed") {
            return games.filter(g => !g.metadata.matchConfirmed);
        }
        return games;
    }

    async function toggleMatchConfirmed(game: GameDto) {
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

    return <div className="flex flex-col gap-4">
        <h1 className="text-2xl font-bold">Manage games in library</h1>
        <div className="flex flex-row gap-2 justify-end">
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
        <Table removeWrapper isStriped isHeaderSticky
               bottomContent={
                   <div className="flex w-full justify-center">
                       {items.length > 0 &&
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
                <TableColumn allowsSorting>Game</TableColumn>
                <TableColumn allowsSorting>Added to library</TableColumn>
                <TableColumn allowsSorting>Download count</TableColumn>
                <TableColumn>Path</TableColumn>
                {/* width={1} keeps the column as far to the right as possible*/}
                <TableColumn width={1}>Actions</TableColumn>
            </TableHeader>
            <TableBody emptyContent="Your filter did not match any games." items={items}>
                {(item) => (
                    <TableRow key={item.id}>
                        <TableCell>
                            <Link href={`/game/${item.id}`}
                                  color="foreground"
                                  className="text-sm"
                                  underline="hover">{item.title} ({item.release !== undefined ? new Date(item.release).getFullYear() : "unknown"})
                            </Link>
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
                        <TableCell className="flex flex-row gap-2">
                            <Button isIconOnly size="sm" onPress={() => toggleMatchConfirmed(item)}>
                                {item.metadata.matchConfirmed ?
                                    <Tooltip content="Unconfirm match">
                                        <CheckCircle weight="fill" className="fill-success"/>
                                    </Tooltip> :
                                    <Tooltip content="Confirm match">
                                        <CheckCircle/>
                                    </Tooltip>}
                            </Button>
                            <Button isIconOnly size="sm" onPress={() => {
                                setSelectedGame(item);
                                editGameModal.onOpenChange();
                            }}>
                                <Tooltip content="Edit metadata">
                                    <Pencil/>
                                </Tooltip>
                            </Button>
                            <Button isIconOnly size="sm" onPress={() => {
                                setSelectedGame(item);
                                matchGameModal.onOpenChange();
                            }}>
                                <Tooltip content="Match game">
                                    <MagnifyingGlass/>
                                </Tooltip>
                            </Button>
                            <Button isIconOnly size="sm" color="danger"
                                    onPress={() => deleteGame(item)}>
                                <Tooltip content="Remove from library">
                                    <Trash/>
                                </Tooltip>
                            </Button>
                        </TableCell>
                    </TableRow>
                )}
            </TableBody>
        </Table>
        <EditGameMetadataModal game={selectedGame}
                               isOpen={editGameModal.isOpen}
                               onOpenChange={editGameModal.onOpenChange}/>
        <MatchGameModal initialSearchTerm={selectedGame.title}
                        isOpen={matchGameModal.isOpen}
                        onOpenChange={matchGameModal.onOpenChange}/>
    </div>;
}