import React from "react";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import Section from "Frontend/components/general/Section";
import * as Yup from 'yup';
import {addToast, Button, Divider, Tooltip, useDisclosure} from "@heroui/react";
import {Plus} from "@phosphor-icons/react";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import {LibraryOverviewCard} from "Frontend/components/general/cards/LibraryOverviewCard";
import LibraryCreationModal from "Frontend/components/general/modals/LibraryCreationModal";
import LibraryUpdateDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryUpdateDto";
import LibraryDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryDto";
import {useSnapshot} from "valtio/react";
import {libraryState} from "Frontend/state/LibraryState";

function LibraryManagementLayout({getConfig, formik}: any) {
    const libraryCreationModal = useDisclosure();
    const state = useSnapshot(libraryState);

    async function updateLibrary(library: LibraryUpdateDto) {
        await LibraryEndpoint.updateLibrary(library);
        addToast({
            title: "Library updated",
            description: `Library ${library.name} has been updated.`,
            color: "success"
        })
    }

    async function removeLibrary(library: LibraryDto) {
        await LibraryEndpoint.deleteLibrary(library.id);
        addToast({
            title: "Library removed",
            description: `Library ${library.name} has been removed.`,
            color: "success"
        })
    }

    return (
        <div className="flex flex-col">
            <Section title="Permissions"/>
            <ConfigFormField configElement={getConfig("library.allow-public-access")} isDisabled/>

            <Section title="Scanning"/>
            <div className="flex flex-col gap-4">
                <ConfigFormField configElement={getConfig("library.scan.enable-filesystem-watcher")} isDisabled/>
                <ConfigFormField configElement={getConfig("library.scan.scan-empty-directories")}/>
                <div className="flex flex-row gap-4 items-baseline">
                    <ConfigFormField configElement={getConfig("library.scan.title-match-min-ratio")}/>
                </div>
                <ConfigFormField configElement={getConfig("library.scan.game-file-extensions")}/>
            </div>

            <Section title="Metadata"/>
            <div className="flex flex-row items-baseline">
                <ConfigFormField configElement={getConfig("library.metadata.update.enabled")} isDisabled/>
                <ConfigFormField configElement={getConfig("library.metadata.update.schedule")}
                                 isDisabled={!formik.values.library.metadata.update.enabled}/>
            </div>

            <div className="flex flex-row items-baseline justify-between">
                <h2 className="text-xl font-bold mt-8 mb-1">Libraries</h2>
                <Tooltip content="Add new library">
                    <Button isIconOnly variant="flat" onPress={libraryCreationModal.onOpen}>
                        <Plus/>
                    </Button>
                </Tooltip>
            </div>
            <Divider className="mb-4"/>
            {state.sorted.length > 0 ?
                // Aspect ratio of cover = 12/17 -> 5 covers = 60/17 -> 353px * 100px
                <div id="library-cards" className="grid gap-4 grid-cols-[repeat(auto-fill,minmax(353px,1fr))]">
                    {state.sorted.map((library) =>
                        // @ts-ignore
                        <LibraryOverviewCard library={library} updateLibrary={updateLibrary}
                                             removeLibrary={removeLibrary} key={library.name}/>
                    )}
                </div> :
                <p className="mt-4 text-center text-default-500">No libraries found</p>
            }

            <LibraryCreationModal
                // @ts-ignore
                libraries={state.sorted}
                isOpen={libraryCreationModal.isOpen}
                onOpenChange={libraryCreationModal.onOpenChange}
            />
        </div>
    );
}

const validationSchema = Yup.object({
    library: Yup.object({
        metadata: Yup.object({
            update: Yup.object({
                // @ts-ignore
                schedule: Yup.string().cron()
            })
        })
    })
});

export const LibraryManagement = withConfigPage(LibraryManagementLayout, "Library Management", validationSchema);