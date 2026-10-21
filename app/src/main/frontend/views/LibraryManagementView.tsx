import {useLocation, useNavigate, useParams} from "react-router";
import React, {useEffect} from "react";
import LibraryHeader from "Frontend/components/general/covers/LibraryHeader";
import {Button, Tabs} from "@heroui/react";
import {ArrowLeftIcon} from "@phosphor-icons/react";
import LibraryManagementDetails from "Frontend/components/general/library/LibraryManagementDetails";
import LibraryManagementGames from "Frontend/components/general/library/LibraryManagementGames";
import {useSnapshot} from "valtio/react";
import {libraryState, useFullLibrary} from "Frontend/state/LibraryState";
import LibraryManagementIgnoredPaths from "Frontend/components/general/library/LibraryManagementIgnoredPaths";

export default function LibraryManagementView() {
    const {libraryId} = useParams();
    const {hash} = useLocation();
    const navigate = useNavigate();
    const state = useSnapshot(libraryState);
    const library = libraryId ? state.state[parseInt(libraryId)] : undefined;
    // Directories and ignored paths are only part of the full DTO
    const {full: fullLibrary} = useFullLibrary(library?.id);

    useEffect(() => {
        if (state.isLoaded && !library) {
            navigate("/administration/games");
        }
    }, [state.isLoaded, library]);

    return library && <div className="flex flex-col gap-4">
        <div className="flex flex-row gap-4 items-center">
            <Button isIconOnly variant="ghost" onPress={() => history.back()}>
                <ArrowLeftIcon/>
            </Button>
            <h1 className="text-2xl font-bold">Manage library</h1>
        </div>
        <LibraryHeader library={library} className="h-32"/>
        <Tabs
            variant="primary"
            selectedKey={hash.length > 0 ? hash : "#details"}
            onSelectionChange={(newKey) => navigate(newKey.toString(), {replace: true})}
            className="w-full"
        >
            <Tabs.ListContainer className="w-full">
                <Tabs.List aria-label="Library management sections" className="w-full">
                    <Tabs.Tab id="#details" className="flex-1 justify-center">
                        Details
                        <Tabs.Indicator/>
                    </Tabs.Tab>
                    <Tabs.Tab id="#games" className="flex-1 justify-center">
                        Games
                        <Tabs.Indicator/>
                    </Tabs.Tab>
                    <Tabs.Tab id="#ignored-paths" className="flex-1 justify-center">
                        Ignored paths
                        <Tabs.Indicator/>
                    </Tabs.Tab>
                </Tabs.List>
            </Tabs.ListContainer>
            <Tabs.Panel id="#details">
                {fullLibrary && <LibraryManagementDetails library={fullLibrary}/>}
            </Tabs.Panel>
            <Tabs.Panel id="#games">
                <LibraryManagementGames library={library}/>
            </Tabs.Panel>
            <Tabs.Panel id="#ignored-paths">
                {fullLibrary && <LibraryManagementIgnoredPaths library={fullLibrary}/>}
            </Tabs.Panel>
        </Tabs>
    </div>;
}
