import React, {useEffect, useState} from "react";
import {Button, Chip, Modal, toast} from "@heroui/react";
import {ListBox, ListBoxItem, useDragAndDrop} from "react-aria-components";
import {CaretUpDownIcon} from "@phosphor-icons/react";
import {useListData} from "@react-stately/data";

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

    // Track order changes to trigger re-renders
    const [orderVersion, setOrderVersion] = useState(0);

    // Update sortedItems when items change
    useEffect(() => {
        sortedItems.setSelectedKeys(new Set());
        sortedItems.items.forEach(item => sortedItems.remove(item.id));
        items.forEach(item => sortedItems.append(item));
        setOrderVersion(prev => prev + 1);
    }, [items]);

    let {dragAndDropHooks} = useDragAndDrop({
        getItems: (keys) =>
            [...keys].map((key) => ({'text/plain': sortedItems.getItem(key)!.name})),
        onReorder(e) {
            if (e.keys.has(e.target.key)) return;

            if (e.target.dropPosition === 'before' || e.target.dropPosition === 'on') {
                sortedItems.moveBefore(e.target.key, e.keys);
            } else if (e.target.dropPosition === 'after') {
                sortedItems.moveAfter(e.target.key, e.keys);
            }
            // Trigger re-render after reorder
            setOrderVersion(prev => prev + 1);
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
                <Modal.Container size="lg">
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
                                             dragAndDropHooks={dragAndDropHooks}
                                             className="flex flex-col gap-2"
                                             key={orderVersion}>
                                        {(item: T) => (
                                            <ListBoxItem
                                                key={item.id}
                                                className="flex flex-row p-2 rounded-lg justify-between items-center bg-foreground/5">
                                                <div className="flex flex-row gap-2 items-center">
                                                    <Chip size="sm" variant="soft" color="accent">
                                                        {sortedItems.items.findIndex(p => p.id === item.id) + 1}
                                                    </Chip>
                                                    <p className="font-normal text-small">{item.name}</p>
                                                </div>
                                                <CaretUpDownIcon/>
                                            </ListBoxItem>
                                        )}
                                    </ListBox>

                                </Modal.Body>
                                <Modal.Footer>
                                    <Button variant="tertiary" onPress={close}>
                                        Cancel
                                    </Button>
                                    <Button variant="primary" onPress={() => updateItemOrder(close)}>
                                        Save
                                    </Button>
                                </Modal.Footer>
                            </>
                        )}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}
