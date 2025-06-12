import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryDto";
import {Button, Pagination, Table, TableBody, TableCell, TableColumn, TableHeader, TableRow} from "@heroui/react";
import {Trash} from "@phosphor-icons/react";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import {useMemo, useState} from "react";
import LibraryUpdateDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryUpdateDto";
import {hashCode} from "Frontend/util/utils";

interface LibraryManagementUnmatchedPathsProps {
    library: LibraryDto;
}

export default function LibraryManagementUnmatchedPaths({library}: LibraryManagementUnmatchedPathsProps) {
    const rowsPerPage = 25;

    const [page, setPage] = useState(1);
    const pages = useMemo(() => {
        return Math.ceil(library.unmatchedPaths!.length / rowsPerPage);
    }, [library]);

    const items = useMemo(() => {
        const start = (page - 1) * rowsPerPage;
        const end = start + rowsPerPage;

        return unmatchedPathItems().slice(start, end);
    }, [page, library]);

    async function deleteUnmatchedPath(unmatchedPath: string) {
        const libraryUpdateDto: LibraryUpdateDto = {
            id: library.id,
            unmatchedPaths: library.unmatchedPaths!.filter((path) => path !== unmatchedPath)
        }
        await LibraryEndpoint.updateLibrary(libraryUpdateDto);
    }

    function unmatchedPathItems(): UnmatchedPathItem[] {
        return library.unmatchedPaths!.map((path) => ({
            key: hashCode(path),
            path: path
        }));
    }

    return <div className="flex flex-col gap-4">
        <h1 className="text-2xl font-bold">Manage unmatched paths</h1>
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
                            <Button isIconOnly size="sm" color="danger"
                                    onPress={() => deleteUnmatchedPath(item.path)}><Trash/>
                            </Button>
                        </TableCell>
                    </TableRow>
                )}
            </TableBody>
        </Table>
    </div>;
}
type UnmatchedPathItem = { key: number; path: string };