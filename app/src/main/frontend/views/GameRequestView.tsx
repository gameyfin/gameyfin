import {
    Button,
    Chip,
    Input,
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
import RequestGameModal from "Frontend/components/general/modals/RequestGameModal";
import {ArrowUp, Check, Info, PlusCircle, Trash, X} from "@phosphor-icons/react";
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
    const requestGameModal = useDisclosure();
    const gameRequests = useSnapshot(gameRequestState).gameRequests;

    const [areGameRequestsEnabled, setAreGameRequestsEnabled] = useState(false);
    const [areGuestsAllowedToRequestGames, setAreGuestsAllowedToRequestGames] = useState(false);

    useEffect(() => {
        ConfigEndpoint.areGameRequestsEnabled().then(setAreGameRequestsEnabled);
        ConfigEndpoint.areGuestsAllowedToRequestGames().then(setAreGuestsAllowedToRequestGames);
    }, []);

    const [searchTerm, setSearchTerm] = useState("");
    const [filters, setFilters] = useState<"all" | GameRequestStatus[]>([GameRequestStatus.PENDING, GameRequestStatus.APPROVED, GameRequestStatus.REJECTED]);
    const [sortDescriptor, setSortDescriptor] = useState<SortDescriptor>({column: "votes", direction: "descending"});

    const [page, setPage] = useState(1);
    const pages = useMemo(() => {
        return Math.ceil(getFilteredRequests().length / rowsPerPage);
    }, [gameRequests, filters]);

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
                        // If votes are equal, sort by creation date (newest first)
                        cmp = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
                    }
                    break;
                case "status":
                    const statusOrder = {
                        [GameRequestStatus.PENDING]: 1,
                        [GameRequestStatus.APPROVED]: 2,
                        [GameRequestStatus.REJECTED]: 3,
                        [GameRequestStatus.FULFILLED]: 4
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
                return <Chip size="sm" radius="sm"
                             className="text-xs bg-success-300 text-success-foreground">Approved</Chip>;
            case GameRequestStatus.FULFILLED:
                return <Chip size="sm" radius="sm" className="text-xs bg-success">Fulfilled</Chip>;
            case GameRequestStatus.REJECTED:
                return <Chip size="sm" radius="sm"
                             className="text-xs bg-danger-300 text-danger-foreground">Rejected</Chip>;
            case GameRequestStatus.PENDING:
            default:
                return <Chip size="sm" radius="sm" className="text-xs">Pending</Chip>;
        }
    }

    return (<>
        <div className="flex flex-row justify-between mb-8">
            <h1 className="text-2xl font-bold">Game Requests</h1>
            <div className="flex flex-row items-center gap-4">
                {!areGameRequestsEnabled &&
                    <SmallInfoField icon={Info}
                                    message="Request submission is disabled"
                                    className="text-foreground/70"/>
                }
                <Button className="w-fit"
                        color="primary"
                        startContent={<PlusCircle weight="fill"/>}
                        onPress={requestGameModal.onOpen}
                        isDisabled={!areGameRequestsEnabled || (!auth.state.user && !areGuestsAllowedToRequestGames)}>
                    Request a Game
                </Button>
            </div>
        </div>


        <div className="flex flex-row gap-2 justify-between mb-4">
            <Input
                className="w-96"
                isClearable
                placeholder="Search"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                onClear={() => setSearchTerm("")}
            />
            <Select
                selectedKeys={filters}
                onSelectionChange={keys => setFilters(Array.from(keys) as any)}
                selectionMode="multiple"
                className="w-64"
            >
                <SelectItem key={GameRequestStatus.PENDING}>Pending</SelectItem>
                <SelectItem key={GameRequestStatus.APPROVED}>Approved</SelectItem>
                <SelectItem key={GameRequestStatus.REJECTED}>Rejected</SelectItem>
                <SelectItem key={GameRequestStatus.FULFILLED}>Fulfilled</SelectItem>
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
               }
        >
            <TableHeader>
                <TableColumn key="title" allowsSorting>Title & Release</TableColumn>
                <TableColumn>Submitted by</TableColumn>
                <TableColumn key="createdAt" allowsSorting>Submitted</TableColumn>
                <TableColumn key="updatedAt" allowsSorting>Updated</TableColumn>
                <TableColumn key="status" allowsSorting>Status</TableColumn>
                {/* width={1} keeps the column as far to the right as possible*/}
                <TableColumn key="votes" allowsSorting width={1}>Votes</TableColumn>
            </TableHeader>
            <TableBody emptyContent="Your search did not match any requests." items={pagedItems}>
                {(item) => (
                    <TableRow key={item.id}>
                        <TableCell>
                            {item.title} ({item.release ? new Date(item.release).getFullYear() : "unknown"})
                        </TableCell>
                        <TableCell>
                            <p className="text-foreground/70">
                                {item.requester ?
                                    item.requester.username :
                                    "Guest"
                                }
                            </p>
                        </TableCell>
                        <TableCell>
                            {new Date(item.createdAt).toLocaleDateString()}
                        </TableCell>
                        <TableCell>
                            {new Date(item.updatedAt).toLocaleDateString()}
                        </TableCell>
                        <TableCell className="min-w-24">
                            {statusToBadge(item.status)}
                        </TableCell>
                        <TableCell>
                            <div className="flex flex-row gap-2">
                                <Tooltip content="Vote for this request">
                                    <Button size="sm"
                                            variant={hasUserVotedForRequest(item as GameRequestDto) ? "solid" : "bordered"}
                                            color={hasUserVotedForRequest(item as GameRequestDto) ? "primary" : "default"}
                                            isDisabled={!auth.state.user || item.status === GameRequestStatus.FULFILLED}
                                            startContent={<ArrowUp/>}
                                            onPress={async () => await toggleVote(item.id)}>
                                        {item.voters.length}
                                    </Button>
                                </Tooltip>
                                {isAdmin(auth) && <div className="flex flex-row gap-2">
                                    <Tooltip content="Approve this request">
                                        <Button size="sm" isIconOnly
                                                variant={item.status === GameRequestStatus.APPROVED ? "solid" : "bordered"}
                                                color={item.status === GameRequestStatus.APPROVED ? "primary" : "default"}
                                                isDisabled={item.status === GameRequestStatus.FULFILLED}
                                                onPress={async () => await toggleApprove(item as GameRequestDto)}>
                                            <Check/>
                                        </Button>
                                    </Tooltip>
                                    <Tooltip content="Reject this request">
                                        <Button size="sm" isIconOnly
                                                variant={item.status === GameRequestStatus.REJECTED ? "solid" : "bordered"}
                                                color={item.status === GameRequestStatus.REJECTED ? "primary" : "default"}
                                                isDisabled={item.status === GameRequestStatus.FULFILLED}
                                                onPress={async () => await toggleReject(item as GameRequestDto)}>
                                            <X/>
                                        </Button>
                                    </Tooltip>
                                </div>}
                                {(isAdmin(auth) || (auth.state.user && item.requester && auth.state.user.id === item.requester.id)) &&
                                    <Tooltip content="Delete this request">
                                        <Button size="sm" isIconOnly
                                                color="danger"
                                                onPress={async () => await deleteRequest(item.id)}>
                                            <Trash/>
                                        </Button>
                                    </Tooltip>
                                }
                            </div>
                        </TableCell>
                    </TableRow>
                )}
            </TableBody>
        </Table>

        <RequestGameModal isOpen={requestGameModal.isOpen}
                          onOpenChange={requestGameModal.onOpenChange}/>
    </>)
}