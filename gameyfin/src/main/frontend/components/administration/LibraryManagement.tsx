import React, {useEffect, useState} from "react";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import Section from "Frontend/components/general/Section";
import * as Yup from 'yup';
import {Button, Divider, Tooltip, useDisclosure} from "@heroui/react";
import {Plus} from "@phosphor-icons/react";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/LibraryDto";
import {LibraryOverviewCard} from "Frontend/components/general/cards/LibraryOverviewCard";
import LibraryCreationModal from "Frontend/components/general/modals/LibraryCreationModal";

function LibraryManagementLayout({getConfig, formik}: any) {
    const [libraries, setLibraries] = useState<LibraryDto[]>([]);
    const libraryCreationModal = useDisclosure();

    useEffect(() => {
        LibraryEndpoint.getAllLibraries().then((response) => {
            if (response === undefined) return;
            let sortedLibraries: LibraryDto[] = response
                .filter(l => !!l)
                .sort((a: LibraryDto, b: LibraryDto) => {
                    if (a.name === undefined || b.name === undefined) return 0;
                    return a.name.localeCompare(b.name);
                });
            setLibraries(sortedLibraries);
        });
    }, []);

    return (
        <div className="flex flex-col">
            <Section title="Permissions"/>
            <ConfigFormField configElement={getConfig("library.allow-public-access")}/>

            <Section title="Scanning"/>
            <ConfigFormField configElement={getConfig("library.scan.enable-filesystem-watcher")}/>

            <Section title="Metadata"/>
            <div className="flex flex-row">
                <ConfigFormField configElement={getConfig("library.metadata.update.enabled")}/>
                <ConfigFormField configElement={getConfig("library.metadata.update.schedule")}
                                 isDisabled={!formik.values.library.metadata.update.enabled}/>
            </div>

            <div className="flex flex-row items-baseline justify-between">
                <h2 className={"text-xl font-bold mt-8 mb-1"}>Libraries</h2>
                <Tooltip content="Add new library">
                    <Button isIconOnly variant="flat" onPress={libraryCreationModal.onOpen}>
                        <Plus/>
                    </Button>
                </Tooltip>
            </div>
            <Divider className="mb-4"/>
            {libraries.length > 0 ?
                // Aspect ratio of cover = 12/17 -> 5 covers = 60/17 -> 353px * 100px
                <div id="library-cards" className="grid gap-4 grid-cols-[repeat(auto-fill,minmax(353px,1fr))]">
                    {libraries.map((library) => <LibraryOverviewCard library={library} key={library.name}/>)}
                </div> :
                "No libraries configured. Add your first library!"
            }

            <LibraryCreationModal
                libraries={libraries}
                setLibraries={setLibraries}
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

export const LibraryManagement = withConfigPage(LibraryManagementLayout, "Library Management", "library", validationSchema);