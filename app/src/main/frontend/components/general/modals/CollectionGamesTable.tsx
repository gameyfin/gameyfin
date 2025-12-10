import {useSnapshot} from "valtio/react";
import {
    Button,
    Input,
    Link,
    Select,
    SelectItem,
    SortDescriptor,
    Table,
    TableBody,
    TableCell,
    TableColumn,
    TableHeader,
    TableRow,
    Tooltip
} from "@heroui/react";
import React, {useMemo, useState} from "react";
import {GameAdminDto} from "Frontend/dtos/GameDtos";
import {CollectionEndpoint} from "Frontend/generated/endpoints";
import {MinusIcon, PlusIcon} from "@phosphor-icons/react";
import LibraryAdminDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryAdminDto";
import {libraryState} from "Frontend/state/LibraryState";
import {gameState} from "Frontend/state/GameState";
import {collectionState} from "Frontend/state/CollectionState";

interface CollectionGamesTableProps {
    collectionId: number;
}

export default function CollectionGamesTable({collectionId}: CollectionGamesTableProps) {
    const gamesState = useSnapshot(gameState);
    const games = gamesState.games as GameAdminDto[];
    const librariesState = useSnapshot(libraryState);
    const libraries = librariesState.state as Record<number, LibraryAdminDto>;
    const collectionsState = useSnapshot(collectionState);
    const collection = collectionsState.state[collectionId];

    const [sortDescriptor, setSortDescriptor] = useState<SortDescriptor>({column: "path", direction: "ascending"});
    const [searchTerm, setSearchTerm] = useState("");
    const [filter, setFilter] = useState<"all" | "inCollection" | "notInCollection">("all");

    function libraryName(game: GameAdminDto) {
        return libraries[game.libraryId]?.name || "Unknown";
    }

    const gameInCollectionMap = useMemo(() => {
        const map = new Map<number, boolean>();
        games.forEach(game => {
            map.set(game.id, collection.gameIds!.includes(game.id));
        });
        return map;
    }, [games, collection.gameIds]);

    function isGameInCollection(game: GameAdminDto) {
        return gameInCollectionMap.get(game.id) ?? false;
    }

    const filteredGames = useMemo(() => {
        return games
            .filter((game) => game.title.toLowerCase().includes(searchTerm.toLowerCase()))
            .filter(game => {
                if (filter === "inCollection") {
                    return isGameInCollection(game);
                } else if (filter === "notInCollection") {
                    return !isGameInCollection(game);
                }
                return true;
            });
    }, [games, searchTerm, filter, gameInCollectionMap]);

    const sortedGames = useMemo(() => {
        return filteredGames
            .slice()
            .sort((a, b) => {
                let cmp: number;
                switch (sortDescriptor.column) {
                    case "title":
                        cmp = a.title.localeCompare(b.title);
                        break;
                    case "library":
                        cmp = (libraryName(a)).localeCompare(libraryName(b));
                        break;
                    default:
                        cmp = 0;
                }
                if (sortDescriptor.direction === "descending") {
                    cmp *= -1;
                }
                return cmp;
            })
            .map(game => ({...game, _inCollection: isGameInCollection(game)}));
    }, [filteredGames, sortDescriptor, libraries, gameInCollectionMap]);

    async function addGameToCollection(game: GameAdminDto) {
        await CollectionEndpoint.addGameToCollection(collectionId, game.id);
    }

    async function removeGameFromCollection(game: GameAdminDto) {
        await CollectionEndpoint.removeGameFromCollection(collectionId, game.id);
    }

    return (
        <div className="flex flex-col gap-2">
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
                    <SelectItem key="all">Show all games</SelectItem>
                    <SelectItem key="inCollection">Show only games in collection</SelectItem>
                    <SelectItem key="notInCollection">Show only games not in collection</SelectItem>
                </Select>
            </div>
            <Table isStriped isHeaderSticky
                   sortDescriptor={sortDescriptor}
                   onSortChange={setSortDescriptor}
                   classNames={{
                       base: "h-96"
                   }}>
                <TableHeader>
                    <TableColumn key="title" allowsSorting>Title</TableColumn>
                    <TableColumn key="library" allowsSorting>Library</TableColumn>
                    <TableColumn width={1}>Actions</TableColumn>
                </TableHeader>
                <TableBody
                    emptyContent="Your filters did not match any games."
                    items={sortedGames}>
                    {(game) => (
                        // Key includes _inCollection to force re-render when that value changes
                        <TableRow key={`${game.id}-${game._inCollection}`}>
                            <TableCell>
                                <Link href={`/game/${game.id}`}
                                      color="foreground"
                                      className="text-sm"
                                      underline="hover">
                                    {game.title} ({game.release ? new Date(game.release).getFullYear() : "unknown"})
                                </Link>
                            </TableCell>
                            <TableCell>
                                <Link href={`/administration/games/library/${game.libraryId}`}
                                      color="foreground"
                                      className="text-sm"
                                      underline="hover">
                                    {libraryName(game)}
                                </Link>
                            </TableCell>
                            <TableCell>
                                <div className="flex flex-row gap-2">
                                    <Tooltip content="Add game to collection">
                                        <Button isIconOnly size="sm"
                                                onPress={() => addGameToCollection(game)}
                                                isDisabled={game._inCollection}>
                                            <PlusIcon/>
                                        </Button>
                                    </Tooltip>
                                    <Tooltip content="Remove game from collection">
                                        <Button isIconOnly size="sm"
                                                onPress={() => removeGameFromCollection(game)}
                                                isDisabled={!game._inCollection}>
                                            <MinusIcon/>
                                        </Button>
                                    </Tooltip>
                                </div>
                            </TableCell>
                        </TableRow>
                    )}
                </TableBody>
            </Table>
        </div>
    );
}