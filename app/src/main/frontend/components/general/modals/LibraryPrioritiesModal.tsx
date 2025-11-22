import React, {useEffect} from "react";
import {addToast, Button, Chip, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader} from "@heroui/react";
import {ListBox, ListBoxItem, useDragAndDrop} from "react-aria-components";
import {CaretUpDownIcon} from "@phosphor-icons/react";
import {useListData} from "@react-stately/data";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import {useSnapshot} from "valtio/react";
import {libraryState} from "Frontend/state/LibraryState";
import LibraryDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryDto";
import LibraryUpdateDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryUpdateDto";

interface LibraryPrioritiesModalProps {
    isOpen: boolean;
    onOpenChange: () => void;
}

export default function LibraryPrioritiesModal({isOpen, onOpenChange}: LibraryPrioritiesModalProps) {

    const libraries = useSnapshot(libraryState).sorted;

    const sortedLibraries = useListData<LibraryDto>({
        initialItems: [],
        getKey: (library) => library.id
    });

    // Update sortedLibraries when libraries change
    useEffect(() => {
        sortedLibraries.setSelectedKeys(new Set());
        sortedLibraries.items.forEach(item => sortedLibraries.remove(item.id));
        (libraries as LibraryDto[]).forEach(library => sortedLibraries.append(library));
    }, [libraries]);

    let {dragAndDropHooks} = useDragAndDrop({
        getItems: (keys) =>
            [...keys].map((key) => ({'text/plain': sortedLibraries.getItem(key)!.name})),
        onReorder(e) {
            if (e.keys.has(e.target.key)) return;

            if (e.target.dropPosition === 'before' || e.target.dropPosition === 'on') {
                sortedLibraries.moveBefore(e.target.key, e.keys);
            } else if (e.target.dropPosition === 'after') {
                sortedLibraries.moveAfter(e.target.key, e.keys);
            }
        }
    });

    async function updateLibraryOrder(onClose: () => void) {
        const updateDtos: LibraryUpdateDto[] = sortedLibraries.items.map((library, index): LibraryUpdateDto => {
            return {
                id: library.id,
                metadata: {
                    displayOnHomepage: library.metadata!.displayOnHomepage,
                    displayOrder: index
                }
            };
        });

        try {
            await LibraryEndpoint.updateLibraries(updateDtos);

            addToast({
                title: "Library order updated",
                description: "Library order has been updated successfully.",
                color: "success"
            });
            onClose();
        } catch (e) {
            addToast({
                title: "Error",
                description: "An error occurred while updating library order.",
                color: "warning"
            });
        }
    }

    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="opaque" size="lg">
            <ModalContent>
                {(onClose) => (
                    <>
                        <ModalHeader className="flex flex-col gap-1">
                            <p>Edit library order</p>
                            <p className="text-small font-normal">
                                Libraries higher on the list are displayed at the start
                            </p>
                        </ModalHeader>
                        <ModalBody>
                            <ListBox items={sortedLibraries.items}
                                     dragAndDropHooks={dragAndDropHooks}
                                     className="flex flex-col gap-2">
                                {(library: LibraryDto) => (
                                    <ListBoxItem
                                        key={library.id}
                                        className="flex flex-row p-2 rounded-lg justify-between items-center bg-foreground/5">
                                        <div className="flex flex-row gap-2 items-center">
                                            <Chip size="sm" color="primary">
                                                {sortedLibraries.items.findIndex(p => p.id === library.id) + 1}
                                            </Chip>
                                            <p className="font-normal text-small">{library.name}</p>
                                        </div>
                                        <CaretUpDownIcon/>
                                    </ListBoxItem>
                                )}
                            </ListBox>

                        </ModalBody>
                        <ModalFooter>
                            <Button variant="light" onPress={onClose}>
                                Cancel
                            </Button>
                            <Button color="primary" onPress={() => updateLibraryOrder(onClose)}>
                                Save
                            </Button>
                        </ModalFooter>
                    </>
                )}
            </ModalContent>
        </Modal>
    );
}