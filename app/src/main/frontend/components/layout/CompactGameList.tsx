import {SortAscendingIcon, SortDescendingIcon} from "@phosphor-icons/react";
import {CSSProperties, ReactNode} from "react";
import {Button, Label, Link, ListBox, Modal, Select, SortDescriptor, Tooltip} from "@heroui/react";
import {List, useDynamicRowHeight} from "react-window";
import GameSummaryDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSummaryDto";
import MetadataCompletenessIndicator from "Frontend/components/general/MetadataCompletenessIndicator";

interface Props {
    games: GameSummaryDto[];
    label: string;
    sortDescriptor: SortDescriptor;
    onSortChange: (value: SortDescriptor) => void;
    sortOptions: { id: string; label: string }[];
    status: (game: GameSummaryDto) => ReactNode;
    actions: (game: GameSummaryDto) => ReactNode;
    onDetails: (game: GameSummaryDto) => void;
}

type RowProps = Pick<Props, "games" | "status" | "actions" | "onDetails">;

function GameRow({index, style, games, status, actions, onDetails}: RowProps & {
    index: number;
    style: CSSProperties
}) {
    const game = games[index];
    return <div style={style} className="px-1 py-2" role="listitem">
        <div className="flex min-w-0 flex-col gap-2 rounded-lg border border-separator bg-surface p-3">
            <Link href={`/game/${game.id}`}
                  className="line-clamp-2 min-w-0 wrap-anywhere text-foreground font-semibold">{game.title}</Link>
            <div className="truncate text-sm text-muted">{status(game)}</div>
            <div className="compact-game-actions mt-auto flex flex-wrap items-center gap-2">
                {actions(game)}
                <Button size="sm" variant="secondary" aria-label={`Details for ${game.title}`}
                        onPress={() => onDetails(game)}>Details</Button>
            </div>
        </div>
    </div>;
}

export default function CompactGameList({games, label, sortDescriptor, onSortChange, sortOptions, ...rowProps}: Props) {
    const rowHeight = useDynamicRowHeight({defaultRowHeight: 190});
    return <section aria-label={label} className="min-w-0 space-y-3">
        <div className="flex flex-wrap items-end gap-2">
            <Select className="min-w-0 flex-1" value={String(sortDescriptor.column)}
                    onChange={column => column != null && onSortChange({...sortDescriptor, column})}>
                <Label>Sort by</Label>
                <Select.Trigger><Select.Value/><Select.Indicator/></Select.Trigger>
                <Select.Popover><ListBox items={sortOptions}>
                    {option => <ListBox.Item id={option.id}
                                             textValue={option.label}>{option.label}<ListBox.ItemIndicator/></ListBox.Item>}
                </ListBox></Select.Popover>
            </Select>
            <Tooltip>
                <Button isIconOnly variant="secondary" className="min-h-11 min-w-11 shrink-0"
                        aria-label={sortDescriptor.direction === "ascending" ? "Ascending" : "Descending"}
                        onPress={() => onSortChange({
                            ...sortDescriptor,
                            direction: sortDescriptor.direction === "ascending" ? "descending" : "ascending"
                        })}>
                    {sortDescriptor.direction === "ascending" ? <SortAscendingIcon aria-hidden="true"/> :
                        <SortDescendingIcon aria-hidden="true"/>}
                </Button>
                <Tooltip.Content>{sortDescriptor.direction === "ascending" ? "Sort descending" : "Sort ascending"}</Tooltip.Content>
            </Tooltip>
        </div>
        {games.length === 0 ? <p className="p-4 text-muted">Your filters did not match any games.</p> :
            <List<RowProps> aria-label={label} role="list" rowComponent={GameRow} rowCount={games.length}
                            rowHeight={rowHeight} rowProps={{games, ...rowProps}} rowKey={index => games[index].id}
                            style={{height: "min(35rem, 65dvh)", width: "100%"}}/>}
    </section>;
}

/** Selection lives in the feature parent, so an open details dialog survives a breakpoint change. */
export function GameDetailsDialog({game, onClose}: { game: GameSummaryDto | null; onClose: () => void }) {
    return <Modal>
        <Modal.Backdrop isOpen={game !== null} onOpenChange={open => !open && onClose()}>
            <Modal.Container size="md" placement="center" scroll="inside">
                <Modal.Dialog>
                    <Modal.CloseTrigger/>
                    <Modal.Header><Modal.Heading>{game?.title}</Modal.Heading></Modal.Header>
                    <Modal.Body className="wrap-anywhere">
                        {game && <dl className="space-y-2">
                            <dt className="text-muted">Path</dt>
                            <dd>{game.path ?? "Unknown"}</dd>
                            <dt className="text-muted">Platforms</dt>
                            <dd>{game.platforms?.join(", ") || "Unspecified"}</dd>
                            <dt className="text-muted">Added to library</dt>
                            <dd>{new Date(game.createdAt).toLocaleString()}</dd>
                            <dt className="text-muted">Download count</dt>
                            <dd>{game.downloadCount ?? 0}</dd>
                            <dt className="text-muted">Completeness</dt>
                            <dd><MetadataCompletenessIndicator game={game}/></dd>
                        </dl>}
                    </Modal.Body>
                    <Modal.Footer><Button onPress={onClose}>Close</Button></Modal.Footer>
                </Modal.Dialog>
            </Modal.Container>
        </Modal.Backdrop>
    </Modal>;
}
