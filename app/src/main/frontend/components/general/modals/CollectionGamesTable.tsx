import {
    Button,
    Link,
    Modal,
    SearchField,
    SortDescriptor,
    Spinner,
    Surface,
    Table,
    TableLayout,
    toast,
    Tooltip,
    useOverlayState,
    Virtualizer
} from "@heroui/react";
import {PlusIcon, XIcon} from "@phosphor-icons/react";
import {useMemo, useRef, useState} from "react";
import {useSnapshot} from "valtio/react";
import CompactGameList, {GameDetailsDialog} from "Frontend/components/layout/CompactGameList";
import ResponsiveDialog from "Frontend/components/layout/ResponsiveDialog";
import {CollectionEndpoint} from "Frontend/generated/endpoints";
import GameSummaryDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSummaryDto";
import {collectionState} from "Frontend/state/CollectionState";
import {gameState} from "Frontend/state/GameState";
import {libraryState} from "Frontend/state/LibraryState";
import {COMPACT_QUERY, useMediaQuery} from "Frontend/util/useMediaQuery";

const sortOptions = [
    {id: "title", label: "Title"},
    {id: "library", label: "Library"},
    {id: "dateAdded", label: "Date added"}
];
const rowHeight = 52;
const headingHeight = 42;

export default function CollectionGamesTable({collectionId, owned}: { collectionId: number; owned: boolean }) {
    const compact = useMediaQuery(COMPACT_QUERY);
    const [selectedDetails, setSelectedDetails] = useState<GameSummaryDto | null>(null);
    const [sortDescriptor, setSortDescriptor] = useState<SortDescriptor>({column: "title", direction: "ascending"});
    const [searchTerm, setSearchTerm] = useState("");
    const [pendingId, setPendingId] = useState<number>();
    const addModal = useOverlayState();
    const {state: games, isLoaded} = useSnapshot(gameState);
    const libraries = useSnapshot(libraryState).state;
    const collection = useSnapshot(collectionState).state[collectionId];
    const dates = collection.metadata.gamesAddedAt;
    const editable = !collection.smartEnabled;

    const members = useMemo(() => {
        const query = searchTerm.trim().toLowerCase();

        function compare(a: GameSummaryDto, b: GameSummaryDto) {
            switch (sortDescriptor.column) {
                case "library":
                    return (libraries[a.libraryId]?.name ?? "").localeCompare(libraries[b.libraryId]?.name ?? "");
                case "dateAdded":
                    return (dates[a.id] ?? "").localeCompare(dates[b.id] ?? "");
                default:
                    return a.title.localeCompare(b.title);
            }
        }

        return collection.gameIds.flatMap(id => {
            const game = games[id];
            return game?.title.toLowerCase().includes(query) ? [game as GameSummaryDto] : [];
        }).sort((a, b) => sortDescriptor.direction === "ascending" ? compare(a, b) : compare(b, a));
    }, [collection.gameIds, dates, games, libraries, searchTerm, sortDescriptor]);

    async function changeMembership(gameId: number, present: boolean) {
        setPendingId(gameId);
        try {
            if (owned) await CollectionEndpoint.setOwnedMembership(collectionId, gameId, present);
            else if (present) await CollectionEndpoint.addGameToCollection(collectionId, gameId);
            else await CollectionEndpoint.removeGameFromCollection(collectionId, gameId);
            return true;
        } catch {
            toast.danger(present ? "Could not add game" : "Could not remove game");
            return false;
        } finally {
            setPendingId(undefined);
        }
    }

    function removeButton(game: GameSummaryDto) {
        return editable && (
            <Tooltip>
                <Button isIconOnly size="sm" className={compact ? "min-h-11 min-w-11" : undefined}
                        variant="ghost" aria-label={`Remove ${game.title} from collection`}
                        onPress={() => changeMembership(game.id, false)}
                        isPending={pendingId === game.id} isDisabled={pendingId !== undefined}>
                    <XIcon aria-hidden="true"/>
                </Button>
                <Tooltip.Content>Remove from collection</Tooltip.Content>
            </Tooltip>
        );
    }

    let emptyMessage;
    if (!isLoaded) emptyMessage = "Loading games…";
    else if (!collection.gameIds.length) {
        emptyMessage = editable ? "This collection has no games yet." : "No games match the current rules.";
    } else emptyMessage = "No games in this collection match your search.";

    return (
        <Surface className="flex min-w-0 flex-col gap-4 rounded-xl p-4 sm:p-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-baseline gap-2">
                    <h2 className="text-lg font-semibold">Games</h2>
                    {collection.gameIds.length > 0 &&
                        <span
                            className="text-sm tabular-nums text-muted">{collection.gameIds.length.toLocaleString()}</span>}
                </div>
                {editable && <Button variant="secondary" onPress={addModal.open}>
                    <PlusIcon aria-hidden="true"/> Add games
                </Button>}
            </div>

            {(collection.gameIds.length > 1 || searchTerm) && (
                <SearchField aria-label="Search collection games" value={searchTerm} onChange={setSearchTerm}
                             variant="secondary"
                             className="w-full sm:max-w-80">
                    <SearchField.Group>
                        <SearchField.SearchIcon/>
                        <SearchField.Input placeholder="Find a game in this collection…"/>
                        <SearchField.ClearButton/>
                    </SearchField.Group>
                </SearchField>
            )}

            {!members.length ? <p className="text-sm text-muted">{emptyMessage}</p> : compact ? (
                <CompactGameList
                    games={members}
                    label="Games in collection"
                    sortDescriptor={sortDescriptor}
                    onSortChange={setSortDescriptor}
                    sortOptions={sortOptions}
                    status={game => libraries[game.libraryId]?.name ?? "Unknown library"}
                    onDetails={setSelectedDetails}
                    actions={removeButton}
                    listHeight={`min(${members.length * 152}px, 35rem, 65dvh)`}
                />
            ) : (
                // Rebuild the virtualized collection when its Actions column changes.
                <Virtualizer key={editable ? "manual" : "smart"} layout={TableLayout}
                             layoutOptions={{headingHeight, rowHeight}}>
                    <Table variant="secondary">
                        <Table.ScrollContainer>
                            <Table.Content aria-label="Games in collection"
                                           className="overflow-auto"
                                           style={{height: Math.min(384, headingHeight + rowHeight * members.length)}}
                                           sortDescriptor={sortDescriptor}
                                           onSortChange={setSortDescriptor}>
                                <Table.Header className="z-10 bg-surface">
                                    {sortOptions.map(({id, label}) => (
                                        <Table.Column key={id} id={id} allowsSorting isRowHeader={id === "title"}>
                                            {({sortDirection}) => (
                                                <Table.SortableColumnHeader sortDirection={sortDirection}>
                                                    {label}
                                                </Table.SortableColumnHeader>
                                            )}
                                        </Table.Column>
                                    ))}
                                    {editable && <Table.Column aria-label="Actions" width={60} className="min-h-9"/>}
                                </Table.Header>
                                <Table.Body items={members}>
                                    {game => (
                                        <Table.Row id={game.id}>
                                            <Table.Cell>
                                                <Link href={`/game/${game.id}`}
                                                      className="line-clamp-1 text-sm text-foreground hover:underline">
                                                    {game.title}{game.release && ` (${new Date(game.release).getFullYear()})`}
                                                </Link>
                                            </Table.Cell>
                                            <Table.Cell>
                                                {owned ? libraries[game.libraryId]?.name ?? "Unknown library" :
                                                    <Link href={`/administration/games/library/${game.libraryId}`}
                                                          className="line-clamp-1 text-sm text-foreground hover:underline">
                                                        {libraries[game.libraryId]?.name ?? "Unknown library"}
                                                    </Link>}
                                            </Table.Cell>
                                            <Table.Cell>{dates[game.id] ? new Date(dates[game.id]).toLocaleDateString() : "—"}</Table.Cell>
                                            {editable && <Table.Cell>{removeButton(game)}</Table.Cell>}
                                        </Table.Row>
                                    )}
                                </Table.Body>
                            </Table.Content>
                        </Table.ScrollContainer>
                    </Table>
                </Virtualizer>
            )}
            {editable && addModal.isOpen &&
                <AddGamesDialog gameIds={collection.gameIds} pendingId={pendingId} onClose={addModal.close}
                                onAdd={gameId => changeMembership(gameId, true)}/>}
            <GameDetailsDialog game={selectedDetails} onClose={() => setSelectedDetails(null)}/>
        </Surface>
    );
}

const resultLimit = 20;

function AddGamesDialog({gameIds, pendingId, onClose, onAdd}: {
    gameIds: readonly number[];
    pendingId?: number;
    onClose: () => void;
    onAdd: (gameId: number) => Promise<boolean>;
}) {
    const [searchTerm, setSearchTerm] = useState("");
    const [added, setAdded] = useState<number[]>([]);
    const searchInput = useRef<HTMLInputElement>(null);
    const {state: games, isLoaded} = useSnapshot(gameState);
    const libraries = useSnapshot(libraryState).state;
    const query = searchTerm.trim().toLowerCase();
    const pending = pendingId !== undefined;
    const matches = useMemo(() => {
        if (!query) return [];
        const excluded = new Set([...gameIds, ...added]);
        return Object.values(games)
            .filter(game => !excluded.has(game.id) && game.title.toLowerCase().includes(query))
            .sort((a, b) => a.title.localeCompare(b.title))
            .slice(0, resultLimit + 1);
    }, [added, gameIds, games, query]);

    async function add(game: Pick<GameSummaryDto, "id" | "title">) {
        if (await onAdd(game.id)) {
            searchInput.current?.focus();
            setAdded(current => [...current, game.id]);
            toast.success(`${game.title} added to collection`);
        }
    }

    return (
        <Modal>
            <Modal.Backdrop isOpen onOpenChange={open => !open && !pending && onClose()} isDismissable={!pending}>
                <ResponsiveDialog size="md" scroll="inside">
                    <Modal.Dialog>
                        <Modal.CloseTrigger isDisabled={pending}/>
                        <Modal.Header><Modal.Heading>Add games</Modal.Heading></Modal.Header>
                        <Modal.Body className="flex flex-col gap-4">
                            <SearchField autoFocus aria-label="Search the game catalog"
                                         value={searchTerm} onChange={setSearchTerm}>
                                <SearchField.Group>
                                    <SearchField.SearchIcon/>
                                    <SearchField.Input ref={searchInput} placeholder="Search by game title…"/>
                                    <SearchField.ClearButton/>
                                </SearchField.Group>
                            </SearchField>
                            {!query ? <p className="text-sm text-muted">Enter a title to find games to add.</p>
                                : !isLoaded ? <Spinner aria-label="Loading games"/>
                                    : !matches.length ?
                                        <p className="text-sm text-muted">No games available to add match your
                                            search.</p>
                                        : (
                                            <ul className="divide-y divide-separator" aria-label="Search results">
                                                {matches.slice(0, resultLimit).map(game => (
                                                    <li key={game.id}
                                                        className="flex items-center justify-between gap-3 py-2">
                                                        <div className="min-w-0">
                                                            <p className="wrap-anywhere font-medium">
                                                                {game.title}
                                                                {game.release && `(${new Date(game.release).getFullYear()})`}
                                                            </p>
                                                            <p className="text-sm text-muted">
                                                                {libraries[game.libraryId]?.name ?? "Unknown library"}
                                                            </p>
                                                        </div>
                                                        <Button size="sm" variant="secondary" className="shrink-0"
                                                                aria-label={`Add ${game.title}`}
                                                                onPress={() => add(game)}
                                                                isPending={pendingId === game.id} isDisabled={pending}>
                                                            <PlusIcon aria-hidden="true"/> Add
                                                        </Button>
                                                    </li>
                                                ))}
                                            </ul>
                                        )}
                            {matches.length > resultLimit && (
                                <p className="text-sm text-muted">Showing the first {resultLimit} results. Refine your
                                    search to see more.</p>
                            )}
                        </Modal.Body>
                        <Modal.Footer>
                            <Button variant="tertiary" onPress={onClose} isDisabled={pending}>Done</Button>
                        </Modal.Footer>
                    </Modal.Dialog>
                </ResponsiveDialog>
            </Modal.Backdrop>
        </Modal>
    );
}
