import {useNavigate, useParams} from "react-router";
import React, {useEffect} from "react";
import LibraryHeader from "Frontend/components/general/covers/LibraryHeader";
import {Button, Tab, Tabs} from "@heroui/react";
import {ArrowLeft} from "@phosphor-icons/react";
import LibraryManagementDetails from "Frontend/components/general/library/LibraryManagementDetails";
import LibraryManagementGames from "Frontend/components/general/library/LibraryManagementGames";
import {useSnapshot} from "valtio/react";
import {initializeLibraryState, libraryState} from "Frontend/state/LibraryState";


export default function LibraryManagementView() {
    const {libraryId} = useParams();
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
        <Tabs color="primary" fullWidth>
            <Tab title="Details">
                {/* @ts-ignore */}
                <LibraryManagementDetails library={state.state[libraryId]}/>
            </Tab>
            <Tab title="Games">
                {/* @ts-ignore */}
                <LibraryManagementGames library={state.state[libraryId]}/>
            </Tab>
            <Tab title="Unmatched paths">
                <p>Unmatched paths</p>
            </Tab>
        </Tabs>
    </div>;
}