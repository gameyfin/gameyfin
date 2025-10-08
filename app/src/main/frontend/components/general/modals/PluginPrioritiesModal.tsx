import React from "react";
import {addToast, Button, Chip, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader} from "@heroui/react";
import {ListBox, ListBoxItem, useDragAndDrop} from "react-aria-components";
import {CaretUpDown} from "@phosphor-icons/react";
import {useListData} from "@react-stately/data";
import PluginDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/PluginDto";
import {PluginEndpoint} from "Frontend/generated/endpoints";

interface PluginPrioritiesModalProps {
    plugins: PluginDto[];
    isOpen: boolean;
    onOpenChange: () => void;
}

export default function PluginPrioritiesModal({plugins, isOpen, onOpenChange}: PluginPrioritiesModalProps) {

    const sortedPlugins = useListData({
        initialItems: plugins, // Already sorted in parent
        getKey: (plugin) => plugin.id
    });

    let {dragAndDropHooks} = useDragAndDrop({
        getItems: (keys) =>
            [...keys].map((key) => ({'text/plain': sortedPlugins.getItem(key)!.name})),
        onReorder(e) {
            if (e.keys.has(e.target.key)) return;

            if (e.target.dropPosition === 'before' || e.target.dropPosition === 'on') {
                sortedPlugins.moveBefore(e.target.key, e.keys);
            } else if (e.target.dropPosition === 'after') {
                sortedPlugins.moveAfter(e.target.key, e.keys);
            }

            // Recalculate priority based on new position (reversed)
            sortedPlugins.items.forEach((plugin, index) => {
                const reversedPriority = sortedPlugins.items.length - index;
                sortedPlugins.update(plugin.id, {...plugin, priority: reversedPriority});
            });
        }
    });

    function generatePrioritiesMap(): Record<string, number> {
        let map: Record<string, number> = {};
        const totalPlugins = sortedPlugins.items.length;
        sortedPlugins.items.forEach((plugin, index) => {
            map[plugin.id] = totalPlugins - index; // Reverse order
        });
        return map;
    }

    async function setPluginPriorities(onClose: () => void) {
        try {
            const prioritiesMap = generatePrioritiesMap();
            await PluginEndpoint.setPluginPriorities(prioritiesMap);

            addToast({
                title: "Plugin order updated",
                description: "Plugin order has been updated successfully.",
                color: "success"
            });
            onClose();
        } catch (e) {
            addToast({
                title: "Error",
                description: "An error occurred while updating plugin order.",
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
                            <p>Edit plugin order</p>
                            <p className="text-small font-normal">Plugins higher on the list are preferred</p>
                        </ModalHeader>
                        <ModalBody>
                            <ListBox items={sortedPlugins.items}
                                     dragAndDropHooks={dragAndDropHooks}
                                     className="flex flex-col gap-2">
                                {(plugin: PluginDto) => (
                                    <ListBoxItem
                                        key={plugin.id}
                                        className="flex flex-row p-2 rounded-lg justify-between items-center bg-foreground/5">
                                        <div className="flex flex-row gap-2 items-center">
                                            <Chip size="sm" color="primary">
                                                {sortedPlugins.items.findIndex(p => p.id === plugin.id) + 1}
                                            </Chip>
                                            <p className="font-normal text-small">{plugin.name}</p>
                                        </div>
                                        <CaretUpDown/>
                                    </ListBoxItem>
                                )}
                            </ListBox>

                        </ModalBody>
                        <ModalFooter>
                            <Button variant="light" onPress={onClose}>
                                Cancel
                            </Button>
                            <Button color="primary" onPress={() => setPluginPriorities(onClose)}>
                                Save
                            </Button>
                        </ModalFooter>
                    </>
                )}
            </ModalContent>
        </Modal>
    );
}