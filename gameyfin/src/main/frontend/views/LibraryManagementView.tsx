import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryDto";
import {useNavigate, useParams} from "react-router";
import React, {useEffect, useState} from "react";
import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import LibraryHeader from "Frontend/components/general/covers/LibraryHeader";
import {Button, Tab, Tabs} from "@heroui/react";
import {ArrowLeft} from "@phosphor-icons/react";
import LibraryManagementDetails from "Frontend/components/general/library/LibraryManagementDetails";
import LibraryManagementGames from "Frontend/components/general/library/LibraryManagementGames";


export default function LibraryManagementView() {
    const {libraryId} = useParams();
    const navigate = useNavigate();
    const [library, setLibrary] = useState<LibraryDto>();
    const [games, setGames] = useState<GameDto[]>([]);

    useEffect(() => {
        if (!libraryId) return;
        LibraryEndpoint.getById(parseInt(libraryId)).then((library: LibraryDto) => {
            setLibrary(library);
        });
        LibraryEndpoint.getGamesInLibrary(parseInt(libraryId)).then((games) => {
            setGames(games);
        });
    }, []);

    return library && <div className="flex flex-col gap-4">
        <div className="flex flex-row gap-4 items-center">
            <Button isIconOnly variant="light" onPress={() => navigate("/administration/libraries")}>
                <ArrowLeft/>
            </Button>
            <h1 className="text-2xl font-bold">Manage library</h1>
        </div>
        <LibraryHeader library={library} maxCoverCount={Math.min(games.length, 10)} className="h-32"/>
        <Tabs color="primary" fullWidth>
            <Tab title="Details">
                <LibraryManagementDetails library={library}/>
            </Tab>
            <Tab title="Games">
                <LibraryManagementGames library={library}/>
            </Tab>
            <Tab title="Unmatched paths">
                <p>Unmatched paths</p>
            </Tab>
        </Tabs>
    </div>;
}