import {
    Button,
    Input,
    Pagination,
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
import {MagnifyingGlassIcon, TrashIcon} from "@phosphor-icons/react";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import {useMemo, useState} from "react";
import LibraryUpdateDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryUpdateDto";
import {fileNameFromPath} from "Frontend/util/utils";
import MatchGameModal from "Frontend/components/general/modals/MatchGameModal";
import LibraryAdminDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryAdminDto";
import IgnoredPathDto from "Frontend/generated/org/gameyfin/app/libraries/dto/IgnoredPathDto";
import IgnoredPathSourceTypeDto from "Frontend/generated/org/gameyfin/app/libraries/dto/IgnoredPathSourceTypeDto";
import {useSnapshot} from "valtio/react";
import {pluginState} from "Frontend/state/PluginState";
import {userState} from "Frontend/state/UserState";
import PluginIcon from "Frontend/components/general/plugin/PluginIcon";
import PluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/PluginDto";

interface LibraryManagementIgnoredPathsProps {
    library: LibraryAdminDto;
}

export default function LibraryManagementIgnoredPaths({library}: LibraryManagementIgnoredPathsProps) {
    const plugins = useSnapshot(pluginState).state;
    const users = useSnapshot(userState).state;

    const matchGameModal = useDisclosure();
    const [page, setPage] = useState(1);
    const rowsPerPage = 25;

    const [searchTerm, setSearchTerm] = useState("");
    const [selectedPath, setSelectedPath] = useState(library.ignoredPaths ? library.ignoredPaths[0] : null);
    const [sortDescriptor, setSortDescriptor] = useState<SortDescriptor>({column: "path", direction: "ascending"});

    const pages = useMemo(() => {
        return Math.ceil(getFilteredPaths().length / rowsPerPage);
    }, [library.ignoredPaths, searchTerm]);

    const filteredPaths = useMemo(() => {
        return library.ignoredPaths!
            .filter((path) => path.path.toLowerCase().includes(searchTerm.toLowerCase()))
            .map((path) => ({key: path.id, path}));
    }, [library, searchTerm]);

    const sortedPaths = useMemo(() => {
        return filteredPaths.slice().sort((a, b) => {
            let cmp: number;
            switch (sortDescriptor.column) {
                case "path":
                    cmp = a.path.path.localeCompare(b.path.path);
                    break;
                default:
                    cmp = 0;
            }
            if (sortDescriptor.direction === "descending") {
                cmp *= -1;
            }
            return cmp;
        });
    }, [filteredPaths, sortDescriptor]);

    const pagedPaths = useMemo(() => {
        const start = (page - 1) * rowsPerPage;
        const end = start + rowsPerPage;
        return sortedPaths.slice(start, end);
    }, [page, sortedPaths]);

    async function deleteIgnoredPath(ignoredPath: IgnoredPathDto) {
        const libraryUpdateDto: LibraryUpdateDto = {
            id: library.id,
            ignoredPaths: library.ignoredPaths!.filter((path) => path.id !== ignoredPath.id)
        }
        await LibraryEndpoint.updateLibrary(libraryUpdateDto);
    }

    function getFilteredPaths() {
        return library.ignoredPaths!!.filter((path) =>
            path.path.toLowerCase().includes(searchTerm.toLowerCase())
        )
    }

    function renderSource(ignoredPath: IgnoredPathDto) {
        if (ignoredPath.sourceType === IgnoredPathSourceTypeDto.USER) {
            const userId = Number(ignoredPath.source);
            const user = users[userId];
            return user ? `Manually added by user (${user.username})` : "Unknown user";
        } else if (ignoredPath.sourceType === IgnoredPathSourceTypeDto.PLUGIN) {
            const pluginIds: string[] = JSON.parse(ignoredPath.source)
            return pluginIds ?
                <div className="flex flex-row gap-2 items-center">
                    <p>Automatically added by plugins (</p>
                    {pluginIds.map(id => {
                        const p = plugins[id];
                        return p ? <PluginIcon key={id} plugin={p as PluginDto}/>
                            : "Unknown plugin";
                    })}
                    <p>)</p>
                </div>
                : "Unknown plugins"
        }
        return ignoredPath.source;
    }

    return <div className="flex flex-col gap-4">
        <h1 className="text-2xl font-bold">Manage ignored paths</h1>
        <Input
            className="w-96"
            isClearable
            placeholder="Search"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            onClear={() => setSearchTerm("")}
        />
        <Table removeWrapper isStriped isHeaderSticky
               sortDescriptor={sortDescriptor}
               onSortChange={setSortDescriptor}
               bottomContent={
                   <div className="flex w-full justify-center">
                       {pagedPaths.length > 0 &&
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
                <TableColumn key="path" allowsSorting>Path</TableColumn>
                <TableColumn key="source">Source</TableColumn>
                <TableColumn width={1}>Actions</TableColumn>
            </TableHeader>
            <TableBody emptyContent="This library has no ignored paths." items={pagedPaths}>
                {(item) => (
                    <TableRow key={item.key}>
                        <TableCell>
                            {item.path.path}
                        </TableCell>
                        <TableCell>
                            {renderSource(item.path)}
                        </TableCell>
                        <TableCell>
                            <div className="flex flex-row gap-2">
                                <Tooltip content="Match game">
                                    <Button isIconOnly size="sm" onPress={() => {
                                        setSelectedPath(item.path);
                                        matchGameModal.onOpenChange();
                                    }}>
                                        <MagnifyingGlassIcon/>
                                    </Button>
                                </Tooltip>
                                <Tooltip content="Remove entry from list">
                                    <Button isIconOnly size="sm" color="danger"
                                            onPress={() => deleteIgnoredPath(item.path)}
                                            isDisabled={item.path.sourceType !== IgnoredPathSourceTypeDto.USER}
                                    >
                                        <TrashIcon/>
                                    </Button>
                                </Tooltip>
                            </div>
                        </TableCell>
                    </TableRow>
                )}
            </TableBody>
        </Table>
        {selectedPath && <MatchGameModal path={selectedPath.path}
                                         libraryId={library.id}
                                         initialSearchTerm={fileNameFromPath(selectedPath.path, false)}
                                         isOpen={matchGameModal.isOpen}
                                         onOpenChange={matchGameModal.onOpenChange}/>
        }
    </div>;
}