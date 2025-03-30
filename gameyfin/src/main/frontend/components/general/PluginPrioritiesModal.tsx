import React, {useEffect} from "react";
import {addToast, Button, Chip, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader} from "@heroui/react";
import {PluginManagementEndpoint} from "Frontend/generated/endpoints";
import PluginDto from "Frontend/generated/de/grimsi/gameyfin/core/plugins/management/PluginDto";
import {ListBox, ListBoxItem, useDragAndDrop} from "react-aria-components";
import {CaretUpDown} from "@phosphor-icons/react";
import {ListData, useListData} from "@react-stately/data";

interface PluginPrioritiesModalProps {
    plugins: PluginDto[];
    isOpen: boolean;
    onOpenChange: () => void;
}

export default function PluginPrioritiesModal({plugins, isOpen, onOpenChange}: PluginPrioritiesModalProps) {

    let sortedPlugins: ListData<PluginDto> = useListData({
        initialItems: plugins,
        getKey: (plugin) => plugin.id!!
    });

    useEffect(() => {
        clearSortedPlugins();
        sortedPlugins.append(...sortPlugins(plugins));
    }, [plugins]);

    function sortPlugins(plugins: PluginDto[]): PluginDto[] {
        return [...plugins].sort((a, b) => {
            if (a.priority === undefined || b.priority === undefined) return 0;
            return b.priority - a.priority;
        });
    }

    function clearSortedPlugins() {
        const keyList = sortedPlugins.items.map(plugin => plugin.id!!);
        keyList.forEach(key => sortedPlugins.remove(key));
    }

    let {dragAndDropHooks} = useDragAndDrop({
        // @ts-ignore
        getItems: (keys) =>
            // @ts-ignore
            [...keys].map((key) => ({'text/plain': sortedPlugins.getItem(key).name})),
        onReorder(e) {
            if (e.keys.has(e.target.key)) {
                return; // Avoid placing a plugin before or after itself
            }

            if (e.target.dropPosition === 'before' || e.target.dropPosition === 'on') {
                sortedPlugins.moveBefore(e.target.key, e.keys);
            } else if (e.target.dropPosition === 'after') {
                sortedPlugins.moveAfter(e.target.key, e.keys);
            }

            // Recalculate priorities
            sortedPlugins.items.forEach((plugin, index) => {
                sortedPlugins.update(plugin.id!!, {...plugin, priority: index + 1});
            });
        }
    });

    function generatePrioritiesMap(): Record<string, number> {
        return sortedPlugins.items.reduce((acc, plugin) => {
            if (plugin.id === undefined || plugin.priority === undefined) return acc;
            acc[plugin.id] = plugin.priority;
            return acc;
        }, {} as Record<string, number>);
    }

    async function setPluginPriorities(onClose: () => void) {
        try {
            const prioritiesMap = generatePrioritiesMap();
            await PluginManagementEndpoint.setPluginPriorities(prioritiesMap);

            addToast({
                title: "Plugin order updated",
                description: "Plugin order have been updated successfully.",
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
                                            <Chip size="sm"
                                                  color="primary">{sortedPlugins.items.length - plugin.priority + 1}</Chip>
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
                            <Button color="success" onPress={() => setPluginPriorities(onClose)}>
                                Save
                            </Button>
                        </ModalFooter>
                    </>
                )}
            </ModalContent>
        </Modal>
    );
}