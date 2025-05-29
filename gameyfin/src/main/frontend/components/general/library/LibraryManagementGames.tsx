import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryDto";
import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import {Button, Table, TableBody, TableCell, TableColumn, TableHeader, TableRow} from "@heroui/react";
import {CheckCircle, Pencil, Trash} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";

interface LibraryManagementGamesProps {
    library: LibraryDto;
}

export default function LibraryManagementGames({library}: LibraryManagementGamesProps) {
    const state = useSnapshot(gameState);
    const games = state.gamesByLibraryId[library.id] ? state.gamesByLibraryId[library.id] as GameDto[] : undefined;

    return <div className="flex flex-col gap-4">
        <h1 className="text-2xl font-bold">Manage games in library</h1>
        <Table removeWrapper isStriped isHeaderSticky>
            <TableHeader>
                <TableColumn allowsSorting>Game</TableColumn>
                <TableColumn allowsSorting>Added to library</TableColumn>
                <TableColumn>Path</TableColumn>
                <TableColumn>Actions</TableColumn>
            </TableHeader>
            <TableBody emptyContent="This library is empty." items={games}>
                {(item) => (
                    <TableRow key={item.id}>
                        <TableCell>
                            {item.title} ({item.release !== undefined ? new Date(item.release).getFullYear() : "unknown"})
                        </TableCell>
                        <TableCell>
                            {new Date(item.createdAt).toLocaleString()}
                        </TableCell>
                        <TableCell>
                            {item.metadata.path}
                        </TableCell>
                        <TableCell className="flex flex-row gap-2">
                            <Button isIconOnly size="sm" isDisabled={true}><CheckCircle/></Button>
                            <Button isIconOnly size="sm" isDisabled={true}><Pencil/></Button>
                            <Button isIconOnly size="sm" isDisabled={true} color="danger"><Trash/></Button>
                        </TableCell>
                    </TableRow>
                )}
            </TableBody>
        </Table>
    </div>;
}