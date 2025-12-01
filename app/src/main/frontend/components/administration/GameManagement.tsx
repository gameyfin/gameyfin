import React from "react";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import Section from "Frontend/components/general/Section";
import * as Yup from 'yup';
import "Frontend/util/yup-extensions";
import {Button, Divider, Tooltip, useDisclosure} from "@heroui/react";
import {ListNumbersIcon, PlusIcon} from "@phosphor-icons/react";
import {LibraryOverviewCard} from "Frontend/components/general/cards/LibraryOverviewCard";
import LibraryCreationModal from "Frontend/components/general/modals/LibraryCreationModal";
import {useSnapshot} from "valtio/react";
import {libraryState} from "Frontend/state/LibraryState";
import LibraryPrioritiesModal from "Frontend/components/general/modals/LibraryPrioritiesModal";
import {collectionState} from "Frontend/state/CollectionState";
import {CollectionOverviewCard} from "Frontend/components/general/cards/CollectionOverviewCard";
import CollectionCreationModal from "Frontend/components/general/modals/CollectionCreationModal";

function GameManagementLayout({getConfig, formik}: any) {
    const libraries = useSnapshot(libraryState);
    const libraryCreationModal = useDisclosure();
    const libraryOrderModal = useDisclosure();

    const collections = useSnapshot(collectionState);
    const collectionCreationModal = useDisclosure();
    const collectionOrderModal = useDisclosure();

    return (
        <div className="flex flex-col">
            <div className="flex flex-row items-baseline justify-between">
                <h2 className="text-xl font-bold mt-8 mb-1">Libraries</h2>
                <div className="flex flex-row gap-2">
                    <Tooltip content="Change library order">
                        <Button isIconOnly variant="flat" onPress={libraryOrderModal.onOpen}>
                            <ListNumbersIcon/>
                        </Button>
                    </Tooltip>
                    <Tooltip content="Add new library">
                        <Button isIconOnly variant="flat" onPress={libraryCreationModal.onOpen}>
                            <PlusIcon/>
                        </Button>
                    </Tooltip>
                </div>
            </div>
            <Divider className="mb-4"/>
            {libraries.sorted.length > 0 ?
                // Aspect ratio of cover = 12/17 -> 5 covers = 60/17 -> 353px * 100px
                <div id="library-cards" className="grid gap-4 grid-cols-[repeat(auto-fill,minmax(353px,1fr))]">
                    {libraries.sorted.map((library) =>
                        // @ts-ignore
                        <LibraryOverviewCard library={library} key={library.name}/>
                    )}
                </div> :
                <p className="mt-4 text-center text-default-500">No libraries found</p>
            }

            <div className="flex flex-row items-baseline justify-between">
                <h2 className="text-xl font-bold mt-8 mb-1">Collections</h2>
                <div className="flex flex-row gap-2">
                    <Tooltip content="Change collection order">
                        <Button isIconOnly variant="flat" onPress={collectionOrderModal.onOpen}>
                            <ListNumbersIcon/>
                        </Button>
                    </Tooltip>
                    <Tooltip content="Add new collection">
                        <Button isIconOnly variant="flat" onPress={collectionCreationModal.onOpen}>
                            <PlusIcon/>
                        </Button>
                    </Tooltip>
                </div>
            </div>
            <Divider className="mb-4"/>
            {collections.sorted.length > 0 ?
                // Aspect ratio of cover = 12/17 -> 5 covers = 60/17 -> 353px * 100px
                <div id="collection-cards" className="grid gap-4 grid-cols-[repeat(auto-fill,minmax(353px,1fr))]">
                    {collections.sorted.map((collection) =>
                        // @ts-ignore
                        <CollectionOverviewCard collection={collection} key={collection.name}/>
                    )}
                </div> :
                <p className="mt-4 text-center text-default-500">No collections found</p>
            }

            <Section title="Scanning"/>
            <div className="flex flex-col gap-4">
                <ConfigFormField configElement={getConfig("library.scan.enable-filesystem-watcher")}/>
                <ConfigFormField configElement={getConfig("library.scan.scan-empty-directories")}/>
                <div className="flex flex-row gap-4 items-baseline">
                    <ConfigFormField configElement={getConfig("library.scan.extract-title-using-regex")}/>
                    <ConfigFormField configElement={getConfig("library.scan.title-extraction-regex")}
                                     isDisabled={!formik.values.library.scan["extract-title-using-regex"]}/>
                </div>
                <ConfigFormField configElement={getConfig("library.scan.title-match-min-ratio")}/>
                <ConfigFormField configElement={getConfig("library.scan.game-file-extensions")}/>
            </div>

            <Section title="Metadata"/>
            <div className="flex flex-row items-baseline">
                <ConfigFormField configElement={getConfig("library.metadata.update.enabled")}/>
                <ConfigFormField configElement={getConfig("library.metadata.update.schedule")}
                                 isDisabled={!formik.values.library.metadata.update.enabled}/>
            </div>

            <LibraryCreationModal
                isOpen={libraryCreationModal.isOpen}
                onOpenChange={libraryCreationModal.onOpenChange}
            />

            <LibraryPrioritiesModal
                isOpen={libraryOrderModal.isOpen}
                onOpenChange={libraryOrderModal.onOpenChange}
            />

            <CollectionCreationModal
                isOpen={collectionCreationModal.isOpen}
                onOpenChange={collectionCreationModal.onOpenChange}
            />

        </div>
    );
}

const validationSchema = Yup.object({
    library: Yup.object({
        metadata: Yup.object({
            update: Yup.object({
                enabled: Yup.boolean(),
                schedule: Yup.string().when("enabled", {
                    is: true,
                    then: (schema) => schema.cron()
                }),
            })
        }),
        scan: Yup.object({
            "extract-title-using-regex": Yup.boolean(),
            "title-extraction-regex": Yup.string().when("extract-title-using-regex", {
                is: true,
                then: (schema) => schema.trim().required("Title extraction regex is required when enabled")
            }),
            "title-match-min-ratio": Yup.number().min(1, "Must be between 1-100").max(100, "Must be between 1-100")
        })
    })
});

export const GameManagement = withConfigPage(GameManagementLayout, "Games", validationSchema);