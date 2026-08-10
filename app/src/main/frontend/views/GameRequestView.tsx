import {
    Button,
    Chip,
    Input,
    ListBox,
    Select,
    SortDescriptor,
    Table,
    Tooltip,
    useOverlayState,
} from "@heroui/react";
import RequestGameModal from "Frontend/components/general/modals/RequestGameModal";
import {ArrowUpIcon, CheckIcon, InfoIcon, PlusCircleIcon, TrashIcon, XIcon} from "@phosphor-icons/react";
import SimplePagination from "Frontend/components/general/SimplePagination";
import React, {useEffect, useMemo, useState} from "react";
import {useAuth} from "Frontend/util/auth";
import {ConfigEndpoint, GameRequestEndpoint} from "Frontend/generated/endpoints";
import {gameRequestState} from "Frontend/state/GameRequestState";
import {useSnapshot} from "valtio/react";
import GameRequestDto from "Frontend/generated/org/gameyfin/app/requests/dto/GameRequestDto";
import GameRequestStatus from "Frontend/generated/org/gameyfin/app/requests/status/GameRequestStatus";
import {isAdmin} from "Frontend/util/utils";
import {SmallInfoField} from "Frontend/components/general/SmallInfoField";

export default function GameRequestView() {
    const rowsPerPage = 25;

    const auth = useAuth();
    const requestGameModal = useOverlayState();
    const gameRequests = useSnapshot(gameRequestState).gameRequests;

    const [areGameRequestsEnabled, setAreGameRequestsEnabled] = useState(false);
    const [areGuestsAllowedToRequestGames, setAreGuestsAllowedToRequestGames] = useState(false);

    useEffect(() => {
        ConfigEndpoint.areGameRequestsEnabled().then(setAreGameRequestsEnabled);
        ConfigEndpoint.areGuestsAllowedToRequestGames().then(setAreGuestsAllowedToRequestGames);
    }, []);

    const [searchTerm, setSearchTerm] = useState("");
    const [filters, setFilters] = useState<GameRequestStatus[]>([
        GameRequestStatus.PENDING,
        GameRequestStatus.APPROVED,
        GameRequestStatus.REJECTED,
    ]);
    const [sortDescriptor, setSortDescriptor] = useState<SortDescriptor>({column: "votes", direction: "descending"});

    const [page, setPage] = useState(1);
    const pages = useMemo(() => {
        return Math.ceil(getFilteredRequests().length / rowsPerPage);
    }, [gameRequests, filters, searchTerm]);

    const filteredItems = useMemo(() => {
        return getFilteredRequests();
    }, [gameRequests, filters, searchTerm]);

    const sortedItems = useMemo(() => {
        return (filteredItems as GameRequestDto[]).slice().sort((a, b) => {
            let cmp: number;

            switch (sortDescriptor.column) {
                case "title":
                    cmp = a.title.localeCompare(b.title);
                    break;
                case "votes":
                    cmp = a.voters.length - b.voters.length;
                    if (cmp === 0) {
                        cmp = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
                    }
                    break;
                case "status":
                    const statusOrder = {
                        [GameRequestStatus.PENDING]: 1,
                        [GameRequestStatus.APPROVED]: 2,
                        [GameRequestStatus.REJECTED]: 3,
                        [GameRequestStatus.FULFILLED]: 4,
                    };
                    cmp = (statusOrder[a.status] || 99) - (statusOrder[b.status] || 99);
                    break;
                case "createdAt":
                    cmp = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
                    break;
                case "updatedAt":
                    cmp = new Date(a.updatedAt).getTime() - new Date(b.updatedAt).getTime();
                    break;
                default:
                    return 0;
            }

            if (sortDescriptor.direction === "descending") {
                cmp *= -1;
            }

            return cmp;
        });
    }, [filteredItems, sortDescriptor]);

    const pagedItems = useMemo(() => {
        const start = (page - 1) * rowsPerPage;
        const end = start + rowsPerPage;
        return sortedItems.slice(start, end);
    }, [page, sortedItems]);

    function getFilteredRequests() {
        let filteredRequests = (gameRequests as GameRequestDto[]).filter((gameRequest) => {
            return gameRequest.title.toLowerCase().includes(searchTerm.toLowerCase()) ||
                (gameRequest.requester && gameRequest.requester.username.toLowerCase().includes(searchTerm.toLowerCase()));
        });

        filteredRequests = filteredRequests.filter((gameRequest) => {
            return filters.includes(gameRequest.status);
        });

        return filteredRequests;
    }

    async function toggleVote(gameRequestId: number) {
        await GameRequestEndpoint.toggleVote(gameRequestId);
    }

    async function toggleApprove(gameRequest: GameRequestDto) {
        if (gameRequest.status == GameRequestStatus.FULFILLED) return;
        const newStatus = gameRequest.status === GameRequestStatus.APPROVED ? GameRequestStatus.PENDING : GameRequestStatus.APPROVED;
        await GameRequestEndpoint.changeStatus(gameRequest.id, newStatus);
    }

    async function toggleReject(gameRequest: GameRequestDto) {
        if (gameRequest.status == GameRequestStatus.FULFILLED) return;
        const newStatus = gameRequest.status === GameRequestStatus.REJECTED ? GameRequestStatus.PENDING : GameRequestStatus.REJECTED;
        await GameRequestEndpoint.changeStatus(gameRequest.id, newStatus);
    }

    async function deleteRequest(gameRequestId: number) {
        await GameRequestEndpoint.delete(gameRequestId);
    }

    function hasUserVotedForRequest(gameRequest: GameRequestDto): boolean {
        if (!auth.state.user) return false;
        return gameRequest.voters.map(v => v.id).includes(auth.state.user.id);
    }

    function statusToBadge(status: GameRequestStatus) {
        switch (status) {
            case GameRequestStatus.APPROVED:
                return <Chip size="sm" className="text-xs bg-success text-success-foreground">Approved</Chip>;
            case GameRequestStatus.FULFILLED:
                return <Chip size="sm" className="text-xs bg-success/20 text-success-foreground">Fulfilled</Chip>;
            case GameRequestStatus.REJECTED:
                return <Chip size="sm" className="text-xs bg-danger/30 text-danger-foreground">Rejected</Chip>;
            case GameRequestStatus.PENDING:
            default:
                return <Chip size="sm" className="text-xs">Pending</Chip>;
        }
    }

    return (<>
        <div className="flex flex-row justify-between mb-8">
            <h1 className="text-2xl font-bold">Game Requests</h1>
            <div className="flex flex-row items-center gap-4">
                {!areGameRequestsEnabled &&
                    <SmallInfoField icon={InfoIcon}
                                    message="Request submission is disabled"
                                    className="text-muted"/>
                }
                <Button
                    className="w-fit"
                    variant="primary"
                    onPress={requestGameModal.open}
                    isDisabled={!areGameRequestsEnabled || (!auth.state.user && !areGuestsAllowedToRequestGames)}
                >
                    <PlusCircleIcon weight="fill"/>
                    Request a Game
                </Button>
            </div>
        </div>

        <div className="flex flex-row gap-2 justify-between mb-4">
            <div className="flex w-96 items-center gap-2 rounded-lg border border-border px-3 py-2">
                <Input
                    className="grow"
                    placeholder="Search"
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                />
                {searchTerm && (
                    <button className="text-muted" onClick={() => setSearchTerm("")} type="button">
                        ×
                    </button>
                )}
            </div>
            <Select
                value={filters}
                onChange={(value) => setFilters(value as GameRequestStatus[])}
                selectionMode="multiple"
                className="w-64"
            >
                <Select.Trigger>
                    <Select.Value/>
                    <Select.Indicator/>
                </Select.Trigger>
                <Select.Popover>
                    <ListBox>
                        <ListBox.Item id={GameRequestStatus.PENDING} key={GameRequestStatus.PENDING} textValue="Pending">
                            Pending
                            <ListBox.ItemIndicator/>
                        </ListBox.Item>
                        <ListBox.Item id={GameRequestStatus.APPROVED} key={GameRequestStatus.APPROVED} textValue="Approved">
                            Approved
                            <ListBox.ItemIndicator/>
                        </ListBox.Item>
                        <ListBox.Item id={GameRequestStatus.REJECTED} key={GameRequestStatus.REJECTED} textValue="Rejected">
                            Rejected
                            <ListBox.ItemIndicator/>
                        </ListBox.Item>
                        <ListBox.Item id={GameRequestStatus.FULFILLED} key={GameRequestStatus.FULFILLED} textValue="Fulfilled">
                            Fulfilled
                            <ListBox.ItemIndicator/>
                        </ListBox.Item>
                    </ListBox>
                </Select.Popover>
            </Select>
        </div>

        <Table>
            <Table.ScrollContainer>
                <Table.Content sortDescriptor={sortDescriptor} onSortChange={setSortDescriptor}>
                    <Table.Header>
                        <Table.Column id="title" allowsSorting>Title & Release</Table.Column>
                        <Table.Column id="platform">Platform</Table.Column>
                        <Table.Column id="requester">Submitted by</Table.Column>
                        <Table.Column id="createdAt" allowsSorting>Submitted</Table.Column>
                        <Table.Column id="updatedAt" allowsSorting>Updated</Table.Column>
                        <Table.Column id="status" allowsSorting>Status</Table.Column>
                        <Table.Column id="votes" allowsSorting width={1}>Votes</Table.Column>
                    </Table.Header>
                    <Table.Body renderEmptyState={() => <p className="text-center text-muted p-4">Your search did not match any requests.</p>} items={pagedItems}>
                        {(item) => (
                            <Table.Row key={item.id}>
                                <Table.Cell>
                                    {item.title} ({item.release ? new Date(item.release).getFullYear() : "unknown"})
                                </Table.Cell>
                                <Table.Cell>
                                    <Chip size="sm" className="text-xs max-w-32 truncate">{item.platform}</Chip>
                                </Table.Cell>
                                <Table.Cell>
                                    <p className="text-muted">
                                        {item.requester ?
                                            item.requester.username :
                                            "Guest"
                                        }
                                    </p>
                                </Table.Cell>
                                <Table.Cell>
                                    {new Date(item.createdAt).toLocaleDateString()}
                                </Table.Cell>
                                <Table.Cell>
                                    {new Date(item.updatedAt).toLocaleDateString()}
                                </Table.Cell>
                                <Table.Cell className="min-w-24">
                                    {statusToBadge(item.status)}
                                </Table.Cell>
                                <Table.Cell>
                                    <div className="flex flex-row gap-2">
                                        <Tooltip>
                                            <Tooltip.Trigger>
                                                <div>
                                                    <Button
                                                        size="sm"
                                                        variant={hasUserVotedForRequest(item as GameRequestDto) ? "primary" : "outline"}
                                                        isDisabled={!auth.state.user || item.status === GameRequestStatus.FULFILLED}
                                                        onPress={async () => await toggleVote(item.id)}
                                                    >
                                                        <ArrowUpIcon/>
                                                        {item.voters.length}
                                                    </Button>
                                                </div>
                                            </Tooltip.Trigger>
                                            <Tooltip.Content placement="left">
                                                {auth.state.user
                                                    ? (item.status === GameRequestStatus.FULFILLED ? "You cannot vote on closed requests" : "Vote for this request")
                                                    : "You must be logged in to vote"}
                                            </Tooltip.Content>
                                        </Tooltip>
                                        {isAdmin(auth) && <div className="flex flex-row gap-2">
                                            <Tooltip>
                                                <Tooltip.Trigger>
                                                    <div>
                                                        <Button
                                                            size="sm"
                                                            isIconOnly
                                                            variant={item.status === GameRequestStatus.APPROVED ? "primary" : "outline"}
                                                            isDisabled={item.status === GameRequestStatus.FULFILLED}
                                                            onPress={async () => await toggleApprove(item as GameRequestDto)}
                                                        >
                                                            <CheckIcon/>
                                                        </Button>
                                                    </div>
                                                </Tooltip.Trigger>
                                                <Tooltip.Content>Approve this request</Tooltip.Content>
                                            </Tooltip>
                                            <Tooltip>
                                                <Tooltip.Trigger>
                                                    <div>
                                                        <Button
                                                            size="sm"
                                                            isIconOnly
                                                            variant={item.status === GameRequestStatus.REJECTED ? "primary" : "outline"}
                                                            isDisabled={item.status === GameRequestStatus.FULFILLED}
                                                            onPress={async () => await toggleReject(item as GameRequestDto)}
                                                        >
                                                            <XIcon/>
                                                        </Button>
                                                    </div>
                                                </Tooltip.Trigger>
                                                <Tooltip.Content>Reject this request</Tooltip.Content>
                                            </Tooltip>
                                        </div>}
                                        {(isAdmin(auth) || (auth.state.user && item.requester && auth.state.user.id === item.requester.id)) &&
                                            <Tooltip>
                                                <Tooltip.Trigger>
                                                    <Button size="sm" isIconOnly variant="danger" onPress={async () => await deleteRequest(item.id)}>
                                                        <TrashIcon/>
                                                    </Button>
                                                </Tooltip.Trigger>
                                                <Tooltip.Content>Delete this request</Tooltip.Content>
                                            </Tooltip>
                                        }
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
                <SimplePagination
                    page={page}
                    total={pages}
                    onChange={(page) => setPage(page)}
                />
            </div>}

        <RequestGameModal isOpen={requestGameModal.isOpen}
                          onOpenChange={requestGameModal.setOpen}/>
    </>);
}
