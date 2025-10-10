import {useLocation, useNavigate, useParams} from "react-router";
import React, {useEffect} from "react";
import LibraryHeader from "Frontend/components/general/covers/LibraryHeader";
import {Button, Tab, Tabs} from "@heroui/react";
import { ArrowLeftIcon } from "@phosphor-icons/react";
import LibraryManagementDetails from "Frontend/components/general/library/LibraryManagementDetails";
import LibraryManagementGames from "Frontend/components/general/library/LibraryManagementGames";
import {useSnapshot} from "valtio/react";
import {libraryState} from "Frontend/state/LibraryState";
import LibraryManagementUnmatchedPaths from "Frontend/components/general/library/LibraryManagementUnmatchedPaths";
import LibraryAdminDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryAdminDto";


export default function LibraryManagementView() {
    const {libraryId} = useParams();
    const {hash} = useLocation();
    const navigate = useNavigate();
    const state = useSnapshot(libraryState);

    useEffect(() => {
        if (state.isLoaded && (!libraryId || !state.state[parseInt(libraryId)])) {
            navigate("/administration/libraries");
        }
    }, [state, libraryId]);

    return libraryId && state.state[parseInt(libraryId)] && <div className="flex flex-col gap-4">
        <div className="flex flex-row gap-4 items-center">
            <Button isIconOnly variant="light" onPress={() => navigate("/administration/libraries")}>
                <ArrowLeftIcon/>
            </Button>
            <h1 className="text-2xl font-bold">Manage library</h1>
        </div>
        <LibraryHeader library={state.state[parseInt(libraryId)] as LibraryAdminDto} className="h-32"/>
        <Tabs color="primary" fullWidth
              selectedKey={hash.length > 0 ? hash : "#details"}
              onSelectionChange={(newKey) => navigate(newKey.toString(), {replace: true})}>
            <Tab key="#details" title="Details">
                <LibraryManagementDetails library={state.state[parseInt(libraryId)] as LibraryAdminDto}/>
            </Tab>
            <Tab key="#games" title="Games">
                <LibraryManagementGames library={state.state[parseInt(libraryId)] as LibraryAdminDto}/>
            </Tab>
            <Tab key="#unmatched-paths" title="Unmatched paths">
                <LibraryManagementUnmatchedPaths library={state.state[parseInt(libraryId)] as LibraryAdminDto}/>
            </Tab>
        </Tabs>
    </div>;
}