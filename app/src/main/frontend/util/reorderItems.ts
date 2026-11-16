import type {ItemDropTarget, Key} from "@react-types/shared";

/** Use the same insertion order for the drag preview and the committed drop. */
export function reorderItems<T>(items: readonly T[], keys: ReadonlySet<Key>, target: ItemDropTarget,
                                getKey: (item: T) => Key): T[] {
    if (keys.has(target.key)) return [...items];
    const moved = items.filter(item => keys.has(getKey(item)));
    const remaining = items.filter(item => !keys.has(getKey(item)));
    const index = remaining.findIndex(item => getKey(item) === target.key);
    if (index < 0 || moved.length === 0) return [...items];
    remaining.splice(index + (target.dropPosition === "after" ? 1 : 0), 0, ...moved);
    return remaining;
}
