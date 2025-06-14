import {useLocation, useNavigate, useParams} from "react-router";
import React, {useEffect} from "react";
import LibraryHeader from "Frontend/components/general/covers/LibraryHeader";
import {Button, Tab, Tabs} from "@heroui/react";
import {ArrowLeft} from "@phosphor-icons/react";
import LibraryManagementDetails from "Frontend/components/general/library/LibraryManagementDetails";
import LibraryManagementGames from "Frontend/components/general/library/LibraryManagementGames";
import {useSnapshot} from "valtio/react";
import {initializeLibraryState, libraryState} from "Frontend/state/LibraryState";
import LibraryManagementUnmatchedPaths from "Frontend/components/general/library/LibraryManagementUnmatchedPaths";


export default function LibraryManagementView() {
    const {libraryId} = useParams();
    const {hash} = useLocation();
    const navigate = useNavigate();
    const state = useSnapshot(libraryState);

    useEffect(() => {
        initializeLibraryState().then((state) => {
            if (!libraryId || !state.state[parseInt(libraryId)]) {
                navigate("/administration/libraries");
            }
        });
    }, [libraryId]);

    return libraryId && state.state[parseInt(libraryId)] && <div className="flex flex-col gap-4">
        <div className="flex flex-row gap-4 items-center">
            <Button isIconOnly variant="light" onPress={() => navigate("/administration/libraries")}>
                <ArrowLeft/>
            </Button>
            <h1 className="text-2xl font-bold">Manage library</h1>
        </div>
        {/* @ts-ignore */}
        <LibraryHeader library={state.state[libraryId]} className="h-32"/>
        {/* @ts-ignore */}
        <Tabs color="primary" fullWidth
              selectedKey={hash.length > 0 ? hash : "#details"}
              onSelectionChange={(newKey) => navigate(newKey.toString(), {replace: true})}>
            <Tab key="#details" title="Details">
                {/* @ts-ignore */}
                <LibraryManagementDetails library={state.state[libraryId]}/>
            </Tab>
            <Tab key="#games" title="Games">
                {/* @ts-ignore */}
                <LibraryManagementGames library={state.state[libraryId]}/>
            </Tab>
            <Tab key="#unmatched-paths" title="Unmatched paths">
                {/* @ts-ignore */}
                <LibraryManagementUnmatchedPaths library={state.state[libraryId]}/>
            </Tab>
        </Tabs>
    </div>;
}