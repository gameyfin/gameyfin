import React, {useState} from "react";
import {CollectionEndpoint, LibraryEndpoint} from "Frontend/generated/endpoints";
import ScanType from "Frontend/generated/org/gameyfin/app/libraries/enums/ScanType";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import Section from "Frontend/components/general/Section";
import * as Yup from 'yup';
import {Button, Link, Separator, Tooltip, useOverlayState} from "@heroui/react";
import {ListNumbersIcon, MagnifyingGlassIcon, MagnifyingGlassPlusIcon, PlusIcon} from "@phosphor-icons/react";
import {LibraryOverviewCard} from "Frontend/components/general/cards/LibraryOverviewCard";
import LibraryCreationModal from "Frontend/components/general/modals/LibraryCreationModal";
import {useSnapshot} from "valtio/react";
import {libraryState} from "Frontend/state/LibraryState";
import LibraryPrioritiesModal from "Frontend/components/general/modals/LibraryPrioritiesModal";
import CollectionKind from "Frontend/generated/org/gameyfin/app/collections/entities/CollectionKind";
import {collectionState} from "Frontend/state/CollectionState";
import {CollectionOverviewCard} from "Frontend/components/general/cards/CollectionOverviewCard";
import CollectionCreationModal from "Frontend/components/general/modals/CollectionCreationModal";
import CollectionPrioritiesModal from "Frontend/components/general/modals/CollectionPrioritiesModal";
import {pluginState} from "Frontend/state/PluginState";
import UploadGameButton from "Frontend/components/general/modals/UploadGameButton";
import BulkDeleteButton from "Frontend/components/general/BulkDeleteButton";

function toggleSelection(selection: Set<number>, id: number) {
    const next = new Set(selection);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    return next;
}

function GameManagementLayout({getConfig, formik}: any) {
    const [selectedLibraries, setSelectedLibraries] = useState<Set<number>>(new Set());
    const [selectedCollections, setSelectedCollections] = useState<Set<number>>(new Set());
    const libraries = useSnapshot(libraryState);
    const libraryCreationModal = useOverlayState();
    const libraryOrderModal = useOverlayState();

    const collections = useSnapshot(collectionState).sorted.filter(collection => collection.kind === CollectionKind.CURATED);
    const collectionCreationModal = useOverlayState();
    const collectionOrderModal = useOverlayState();

    const hasActiveMetadataPlugins = useSnapshot(pluginState).hasActiveMetadataPlugins;

    async function triggerScan(scanType: ScanType) {
        await LibraryEndpoint.triggerScan(scanType, undefined);
    }

    return (
        <div className="flex flex-col">
            <div className="flex flex-wrap gap-3 items-baseline justify-between">
                <h2 className="text-xl font-bold mt-8 mb-1">Libraries</h2>
                <div className="flex flex-row gap-2">
                    <BulkDeleteButton entityLabel="libraries"
                                      selectedItems={libraries.sorted.filter(item => selectedLibraries.has(item.id))}
                                      deleteItem={id => LibraryEndpoint.deleteLibrary(id)}
                                      onStarted={ids => setSelectedLibraries(current => new Set(
                                          [...current].filter(id => !ids.includes(id))
                                      ))}/>
                    <Tooltip delay={0}>
                        <Tooltip.Trigger>
                            <Button isIconOnly variant="ghost"
                                    isDisabled={!hasActiveMetadataPlugins}
                                    onPress={() => triggerScan(ScanType.QUICK)}>
                                <MagnifyingGlassIcon/>
                            </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content>
                            <p>Scan all libraries (quick)</p>
                        </Tooltip.Content>
                    </Tooltip>
                    <Tooltip delay={0}>
                        <Tooltip.Trigger>
                            <Button isIconOnly variant="ghost"
                                    isDisabled={!hasActiveMetadataPlugins}
                                    onPress={() => triggerScan(ScanType.FULL)}>
                                <MagnifyingGlassPlusIcon/>
                            </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content>
                            <p>Scan all libraries (full)</p>
                        </Tooltip.Content>
                    </Tooltip>
                    <Tooltip delay={0}>
                        <Tooltip.Trigger>
                            <Button isIconOnly variant="ghost" onPress={libraryOrderModal.open}>
                                <ListNumbersIcon/>
                            </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content>
                            <p>Change library order</p>
                        </Tooltip.Content>
                    </Tooltip>
                    <Tooltip delay={0}>
                        <Tooltip.Trigger>
                            <Button isIconOnly variant="ghost" onPress={libraryCreationModal.open}>
                                <PlusIcon/>
                            </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content>
                            <p>Add new library</p>
                        </Tooltip.Content>
                    </Tooltip>
                </div>
            </div>
            <Separator className="mb-4"/>
            {libraries.sorted.length > 0 ?
                // Aspect ratio of cover = 12/17 -> 5 covers = 60/17 -> 353px * 100px
                <ul id="library-cards" aria-label="Libraries"
                    className="grid gap-4 grid-cols-[repeat(auto-fill,minmax(min(100%,353px),1fr))]">
                    {libraries.sorted.map((library) =>
                        <LibraryOverviewCard library={library} key={library.id}
                                             isSelected={selectedLibraries.has(library.id)}
                                             onToggleSelection={() => setSelectedLibraries(current => toggleSelection(current, library.id))}/>
                    )}
                </ul> :
                <p className="mt-4 text-center text-muted">No libraries found</p>
            }

            <div className="flex flex-wrap gap-3 items-baseline justify-between">
                <h2 className="text-xl font-bold mt-8 mb-1">Collections</h2>
                <div className="flex flex-row gap-2">
                    <BulkDeleteButton entityLabel="collections"
                                      selectedItems={collections.filter(item => selectedCollections.has(item.id))}
                                      deleteItem={id => CollectionEndpoint.deleteCollection(id)}
                                      onStarted={ids => setSelectedCollections(current => new Set(
                                          [...current].filter(id => !ids.includes(id))
                                      ))}/>
                    <Tooltip delay={0}>
                        <Tooltip.Trigger>
                            <Button isIconOnly variant="ghost" onPress={collectionOrderModal.open}>
                                <ListNumbersIcon/>
                            </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content>
                            <p>Change collection order</p>
                        </Tooltip.Content>
                    </Tooltip>
                    <Tooltip delay={0}>
                        <Tooltip.Trigger>
                            <Button isIconOnly variant="ghost" onPress={collectionCreationModal.open}>
                                <PlusIcon/>
                            </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content>
                            <p>Create new collection</p>
                        </Tooltip.Content>
                    </Tooltip>
                </div>
            </div>
            <Separator className="mb-4"/>
            {collections.length > 0 ?
                // Aspect ratio of cover = 12/17 -> 5 covers = 60/17 -> 353px * 100px
                <ul id="collection-cards" aria-label="Collections"
                    className="grid gap-4 grid-cols-[repeat(auto-fill,minmax(min(100%,353px),1fr))]">
                    {collections.map((collection) =>
                        <CollectionOverviewCard collection={collection} href={`/administration/games/collection/${collection.id}`} key={collection.id}
                                                isSelected={selectedCollections.has(collection.id)}
                                                onToggleSelection={() => setSelectedCollections(current => toggleSelection(current, collection.id))}/>
                    )}
                </ul> :
                <p className="mt-4 text-center text-muted">No collections found</p>
            }

            <Section title="Scanning"/>
            <div className="flex flex-col gap-4">
                <ConfigFormField configElement={getConfig("library.scan.enable-filesystem-watcher")}/>
                <ConfigFormField configElement={getConfig("library.scan.scan-empty-directories")}/>
                <div className="flex flex-col xl:flex-row gap-4 items-start">
                    <ConfigFormField className="xl:pt-8"
                                     configElement={getConfig("library.scan.extract-title-using-regex")}/>
                    <ConfigFormField configElement={getConfig("library.scan.title-extraction-regex")}
                                     isDisabled={!formik.values.library.scan["extract-title-using-regex"]}/>
                </div>
                <ConfigFormField configElement={getConfig("library.scan.title-match-min-ratio")}/>
                <ConfigFormField configElement={getConfig("library.scan.game-file-extensions")}/>
                <ConfigFormField configElement={getConfig("library.scan.max-concurrency")}/>
            </div>

            <p className="text-sm text-muted mt-2">
                Scheduled scans of all libraries can be configured on the{" "}
                <Link className="underline text-sm" href="/administration/tasks">tasks page</Link>.
            </p>

            <LibraryCreationModal
                isOpen={libraryCreationModal.isOpen}
                onOpenChange={libraryCreationModal.toggle}
            />

            <LibraryPrioritiesModal
                isOpen={libraryOrderModal.isOpen}
                onOpenChange={libraryOrderModal.toggle}
            />

            <CollectionCreationModal
                isOpen={collectionCreationModal.isOpen}
                onOpenChange={collectionCreationModal.toggle}
            />

            <CollectionPrioritiesModal
                isOpen={collectionOrderModal.isOpen}
                onOpenChange={collectionOrderModal.toggle}
            />

        </div>
    );
}

const validationSchema = Yup.object({
    library: Yup.object({
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

export const GameManagement = withConfigPage(GameManagementLayout, "Games", validationSchema, <UploadGameButton/>);
