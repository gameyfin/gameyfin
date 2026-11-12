import ResponsiveDialog from "Frontend/components/layout/ResponsiveDialog";
import React, {useEffect} from "react";
import {Button, Chip, Modal, toast} from "@heroui/react";
import {ListBox, ListBoxItem} from "react-aria-components";
import {CaretUpDownIcon} from "@phosphor-icons/react";
import {useListData} from "@react-stately/data";
import {useAnimatedReorder} from "Frontend/util/useAnimatedReorder";

export interface PrioritizableItem {
    id: number | string;
    name: string;
}

interface PrioritiesModalProps<T extends PrioritizableItem> {
    title: string;
    subtitle: string;
    items: T[];
    updateItems: (items: T[]) => Promise<void>;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
}

export default function PrioritiesModal<T extends PrioritizableItem>({
                                                                         items,
                                                                         isOpen,
                                                                         onOpenChange,
                                                                         title,
                                                                         subtitle,
                                                                         updateItems
                                                                     }: PrioritiesModalProps<T>) {

    const sortedItems = useListData<T>({
        initialItems: items,
        getKey: (item) => item.id
    });

    // Update sortedItems when items change
    useEffect(() => {
        sortedItems.setSelectedKeys(new Set());
        sortedItems.items.forEach(item => sortedItems.remove(item.id));
        items.forEach(item => sortedItems.append(item));
    }, [items]);

    const {ref, dragAndDropHooks} = useAnimatedReorder({
        items: sortedItems.items,
        getKey: item => item.id,
        getLabel: item => item.name,
        onReorder(reordered) {
            // Move existing items without remounting the collection or losing focus.
            reordered.slice(1).forEach((item, index) => sortedItems.moveAfter(reordered[index].id, [item.id]));
        }
    });

    async function updateItemOrder(onClose: () => void) {
        try {
            // Pass the reordered items directly to the update function
            // The parent component will handle the actual transformation
            await updateItems(sortedItems.items);

            toast("Order updated", {
                description: "Item order has been updated successfully.",
                variant: "success"
            });
            onClose();
        } catch (e) {
            toast("Error", {
                description: "An error occurred while updating item order.",
                variant: "warning"
            });
        }
    }

    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} variant="opaque">
                <ResponsiveDialog>
                    <Modal.Dialog>
                        {({close}) => (
                            <>
                                <Modal.CloseTrigger/>
                                <Modal.Header className="flex flex-col gap-1">
                                    <Modal.Heading>{title}</Modal.Heading>
                                    <p className="text-small font-normal">{subtitle}</p>
                                </Modal.Header>
                                <Modal.Body>
                                    <ListBox items={sortedItems.items}
                                             dependencies={[sortedItems.items]}
                                             ref={ref}
                                             aria-label={title}
                                             dragAndDropHooks={dragAndDropHooks}
                                             className="flex flex-col gap-2 max-h-[60vh] overflow-y-auto pr-1">
                                        {(item: T) => (
                                            <ListBoxItem
                                                key={item.id}
                                                id={item.id}
                                                textValue={item.name}
                                                data-reorder-key={String(item.id)}
                                                className="group/priority rounded-lg outline-none focus-visible:ring-2 focus-visible:ring-accent">
                                                <div data-reorder-content
                                                     className="flex flex-row p-2 rounded-lg justify-between items-center bg-foreground/5 group-data-dragging/priority:opacity-40">
                                                    <div className="flex flex-row gap-2 items-center">
                                                        <Chip size="sm" variant="soft" color="accent">
                                                            {sortedItems.items.findIndex(p => p.id === item.id) + 1}
                                                        </Chip>
                                                        <p className="font-normal text-small">{item.name}</p>
                                                    </div>
                                                    <CaretUpDownIcon/>
                                                </div>
                                            </ListBoxItem>
                                        )}
                                    </ListBox>

                                </Modal.Body>
                                <Modal.Footer>
                                    <Button variant="ghost" onPress={close}>
                                        Cancel
                                    </Button>
                                    <Button variant="primary" onPress={() => updateItemOrder(close)}>
                                        Save
                                    </Button>
                                </Modal.Footer>
                            </>
                        )}
                    </Modal.Dialog>
                </ResponsiveDialog>
            </Modal.Backdrop>
        </Modal>
    );
}
