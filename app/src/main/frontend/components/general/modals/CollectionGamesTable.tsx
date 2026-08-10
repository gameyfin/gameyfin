import {useSnapshot} from "valtio/react";
import {
    Button,
    Input,
    ListBox,
    Link,
    Select,
    SortDescriptor,
    Table,
    Tooltip
} from "@heroui/react";
import React, {useMemo, useState} from "react";
import {GameAdminDto} from "Frontend/dtos/GameDtos";
import {CollectionEndpoint} from "Frontend/generated/endpoints";
import {MinusIcon, PlusIcon, XIcon} from "@phosphor-icons/react";
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
                    case "dateAdded":
                        cmp = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
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
                <div className="w-96 relative">
                    <Input
                        className="w-full pr-8"
                        placeholder="Search"
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                    />
                    {searchTerm && (
                        <button
                            type="button"
                            className="absolute right-2 top-1/2 -translate-y-1/2 text-muted"
                            onClick={() => setSearchTerm("")}
                        >
                            <XIcon/>
                        </button>
                    )}
                </div>
                <Select value={filter}
                        onChange={(value) => setFilter(value as "all" | "inCollection" | "notInCollection")}
                        className="w-64">
                    <Select.Trigger>
                        <Select.Value/>
                        <Select.Indicator/>
                    </Select.Trigger>
                    <Select.Popover>
                        <ListBox>
                            <ListBox.Item id="all" textValue="Show all games">
                                Show all games
                                <ListBox.ItemIndicator/>
                            </ListBox.Item>
                            <ListBox.Item id="inCollection" textValue="Show only games in collection">
                                Show only games in collection
                                <ListBox.ItemIndicator/>
                            </ListBox.Item>
                            <ListBox.Item id="notInCollection" textValue="Show only games not in collection">
                                Show only games not in collection
                                <ListBox.ItemIndicator/>
                            </ListBox.Item>
                        </ListBox>
                    </Select.Popover>
                </Select>
            </div>
            <Table className="h-96">
                <Table.ScrollContainer className="h-96">
                    <Table.Content sortDescriptor={sortDescriptor} onSortChange={setSortDescriptor}>
                        <Table.Header>
                            <Table.Column id="title" allowsSorting>Title</Table.Column>
                            <Table.Column id="library" allowsSorting>Library</Table.Column>
                            <Table.Column id="dateAdded" allowsSorting>Date added</Table.Column>
                            <Table.Column>Actions</Table.Column>
                        </Table.Header>
                        <Table.Body
                            renderEmptyState={() => <p className="text-center text-muted p-4">Your filters did not match any games.</p>}
                            items={sortedGames}>
                            {(game) => (
                                <Table.Row key={`${game.id}-${game._inCollection}`}>
                                    <Table.Cell>
                                        <Link href={`/game/${game.id}`}
                                              className="text-sm text-foreground hover:underline">
                                            {game.title} ({game.release ? new Date(game.release).getFullYear() : "unknown"})
                                        </Link>
                                    </Table.Cell>
                                    <Table.Cell>
                                        <Link href={`/administration/games/library/${game.libraryId}`}
                                              className="text-sm text-foreground hover:underline">
                                            {libraryName(game)}
                                        </Link>
                                    </Table.Cell>
                                    <Table.Cell>
                                        {new Date(game.createdAt).toLocaleString()}
                                    </Table.Cell>
                                    <Table.Cell>
                                        <div className="flex flex-row gap-2">
                                            <Tooltip delay={0}>
                                                <Tooltip.Trigger>
                                                    <span className="inline-flex">
                                                        <Button isIconOnly size="sm"
                                                                onPress={() => addGameToCollection(game)}
                                                                isDisabled={game._inCollection}>
                                                            <PlusIcon/>
                                                        </Button>
                                                    </span>
                                                </Tooltip.Trigger>
                                                <Tooltip.Content>Add game to collection</Tooltip.Content>
                                            </Tooltip>
                                            <Tooltip delay={0}>
                                                <Tooltip.Trigger>
                                                    <span className="inline-flex">
                                                        <Button isIconOnly size="sm"
                                                                onPress={() => removeGameFromCollection(game)}
                                                                isDisabled={!game._inCollection}>
                                                            <MinusIcon/>
                                                        </Button>
                                                    </span>
                                                </Tooltip.Trigger>
                                                <Tooltip.Content>Remove game from collection</Tooltip.Content>
                                            </Tooltip>
                                        </div>
                                    </Table.Cell>
                                </Table.Row>
                            )}
                        </Table.Body>
                    </Table.Content>
                </Table.ScrollContainer>
            </Table>
        </div>
    );
}