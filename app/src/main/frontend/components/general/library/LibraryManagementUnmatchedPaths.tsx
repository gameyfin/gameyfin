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
import { MagnifyingGlassIcon, TrashIcon } from "@phosphor-icons/react";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import {useMemo, useState} from "react";
import LibraryUpdateDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryUpdateDto";
import {fileNameFromPath, hashCode} from "Frontend/util/utils";
import MatchGameModal from "Frontend/components/general/modals/MatchGameModal";
import LibraryAdminDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryAdminDto";

interface LibraryManagementUnmatchedPathsProps {
    library: LibraryAdminDto;
}

export default function LibraryManagementUnmatchedPaths({library}: LibraryManagementUnmatchedPathsProps) {
    const matchGameModal = useDisclosure();
    const [page, setPage] = useState(1);
    const rowsPerPage = 25;

    const [searchTerm, setSearchTerm] = useState("");
    const [selectedPath, setSelectedPath] = useState(library.unmatchedPaths ? library.unmatchedPaths[0] : null);
    const [sortDescriptor, setSortDescriptor] = useState<SortDescriptor>({column: "path", direction: "ascending"});

    const pages = useMemo(() => {
        return Math.ceil(getFilteredPaths().length / rowsPerPage);
    }, [library.unmatchedPaths, searchTerm]);

    const filteredPaths = useMemo(() => {
        return library.unmatchedPaths!
            .filter((path) => path.toLowerCase().includes(searchTerm.toLowerCase()))
            .map((path) => ({key: hashCode(path), path}));
    }, [library, searchTerm]);

    const sortedPaths = useMemo(() => {
        return filteredPaths.slice().sort((a, b) => {
            let cmp: number;
            switch (sortDescriptor.column) {
                case "path":
                    cmp = a.path.localeCompare(b.path);
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

    async function deleteUnmatchedPath(unmatchedPath: string) {
        const libraryUpdateDto: LibraryUpdateDto = {
            id: library.id,
            unmatchedPaths: library.unmatchedPaths!.filter((path) => path !== unmatchedPath)
        }
        await LibraryEndpoint.updateLibrary(libraryUpdateDto);
    }

    function getFilteredPaths() {
        return library.unmatchedPaths!!.filter((path) =>
            path.toLowerCase().includes(searchTerm.toLowerCase())
        )
    }

    return <div className="flex flex-col gap-4">
        <h1 className="text-2xl font-bold">Manage unmatched paths</h1>
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
                <TableColumn width={1}>Actions</TableColumn>
            </TableHeader>
            <TableBody emptyContent="This library has no unmatched paths." items={pagedPaths}>
                {(item) => (
                    <TableRow key={item.key}>
                        <TableCell>
                            {item.path}
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
                                            onPress={() => deleteUnmatchedPath(item.path)}><TrashIcon/>
                                    </Button>
                                </Tooltip>
                            </div>
                        </TableCell>
                    </TableRow>
                )}
            </TableBody>
        </Table>
        {selectedPath && <MatchGameModal path={selectedPath}
                                         libraryId={library.id}
                                         initialSearchTerm={fileNameFromPath(selectedPath, false)}
                                         isOpen={matchGameModal.isOpen}
                                         onOpenChange={matchGameModal.onOpenChange}/>
        }
    </div>;
}