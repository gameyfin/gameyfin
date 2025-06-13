import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryDto";
import {
    Button,
    Input,
    Pagination,
    Table,
    TableBody,
    TableCell,
    TableColumn,
    TableHeader,
    TableRow,
    Tooltip,
    useDisclosure
} from "@heroui/react";
import {MagnifyingGlass, Trash} from "@phosphor-icons/react";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import {useMemo, useState} from "react";
import LibraryUpdateDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryUpdateDto";
import {fileNameFromPath, hashCode} from "Frontend/util/utils";
import MatchGameModal from "Frontend/components/general/modals/MatchGameModal";

interface LibraryManagementUnmatchedPathsProps {
    library: LibraryDto;
}

export default function LibraryManagementUnmatchedPaths({library}: LibraryManagementUnmatchedPathsProps) {
    const matchGameModal = useDisclosure();
    const [searchTerm, setSearchTerm] = useState("");
    const [page, setPage] = useState(1);
    const rowsPerPage = 25;

    const filteredItems = useMemo(() => {
        return library.unmatchedPaths!
            .filter((path) => path.toLowerCase().includes(searchTerm.toLowerCase()))
            .map((path) => ({key: hashCode(path), path}));
    }, [searchTerm, library]);

    const pages = useMemo(() => Math.ceil(filteredItems.length / rowsPerPage), [filteredItems]);
    const items = useMemo(() => {
        const start = (page - 1) * rowsPerPage;
        return filteredItems.slice(start, start + rowsPerPage);
    }, [page, filteredItems]);

    const [selectedPath, setSelectedPath] = useState(library.unmatchedPaths ? library.unmatchedPaths[0] : null);

    async function deleteUnmatchedPath(unmatchedPath: string) {
        const libraryUpdateDto: LibraryUpdateDto = {
            id: library.id,
            unmatchedPaths: library.unmatchedPaths!.filter((path) => path !== unmatchedPath)
        }
        await LibraryEndpoint.updateLibrary(libraryUpdateDto);
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
                <TableColumn allowsSorting>Path</TableColumn>
                <TableColumn width={1}>Actions</TableColumn>
            </TableHeader>
            <TableBody emptyContent="This library has no unmatched paths." items={items}>
                {(item) => (
                    <TableRow key={item.key}>
                        <TableCell>
                            {item.path}
                        </TableCell>
                        <TableCell className="flex flex-row gap-2">
                            <Tooltip content="Match game">
                                <Button isIconOnly size="sm" onPress={() => {
                                    setSelectedPath(item.path);
                                    matchGameModal.onOpenChange();
                                }}>
                                    <MagnifyingGlass/>
                                </Button>
                            </Tooltip>
                            <Tooltip content="Remove entry from list">
                                <Button isIconOnly size="sm" color="danger"
                                        onPress={() => deleteUnmatchedPath(item.path)}><Trash/>
                                </Button>
                            </Tooltip>
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