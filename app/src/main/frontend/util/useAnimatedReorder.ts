import {createElement, useLayoutEffect, useRef, useState} from "react";
import {DropIndicator, useDragAndDrop} from "react-aria-components";
import type {ItemDropTarget, Key} from "@react-types/shared";
import {reorderItems} from "./reorderItems";

/** Animate card contents while leaving React Aria's hit areas in their original slots. */
export function useAnimatedReorder<T>({items, getKey, getLabel, onReorder, disabled = false}: {
    items: readonly T[];
    getKey: (item: T) => Key;
    getLabel: (item: T) => string;
    onReorder: (items: T[]) => void;
    disabled?: boolean;
}) {
    const ref = useRef<HTMLDivElement>(null);
    const draggedKeys = useRef<Set<Key>>(new Set());
    const [target, setTarget] = useState<ItemDropTarget | null>(null);
    const previousSlots = useRef(new Map<string, { x: number; y: number }>());
    const animations = useRef(new Map<HTMLElement, Animation>());
    const isPreviewing = !disabled && target !== null && draggedKeys.current.size > 0;
    const preview = isPreviewing ? reorderItems(items, draggedKeys.current, target, getKey) : items;

    const {dragAndDropHooks} = useDragAndDrop({
        isDisabled: disabled,
        getItems: keys => items.filter(item => keys.has(getKey(item))).map(item => ({"text/plain": getLabel(item)})),
        getAllowedDropOperations: () => ["move"],
        // The animated items show the destination; retain only the accessible drop targets.
        renderDropIndicator: target => createElement(DropIndicator, {target, className: "sr-only"}),
        onDragStart: event => {
            draggedKeys.current = event.keys;
        },
        onDropEnter: event => setTarget(event.target.type === "item" ? event.target : null),
        onDropExit: () => setTarget(null),
        onDragEnd: () => {
            draggedKeys.current = new Set();
            setTarget(null);
        },
        onReorder: event => {
            onReorder(reorderItems(items, event.keys, event.target, getKey));
            setTarget(null);
        }
    });

    useLayoutEffect(() => {
        const container = ref.current;
        if (!container) return;
        const bounds = container.getBoundingClientRect();
        const cards = [...container.querySelectorAll<HTMLElement>("[data-reorder-key]")];
        const slots = cards.map(card => {
            const rect = card.getBoundingClientRect();
            return {x: rect.left - bounds.left + container.scrollLeft, y: rect.top - bounds.top + container.scrollTop};
        });
        const destinations = new Map(preview.map((item, index) => [String(getKey(item)), slots[index]]));
        const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
        const collectionCommitted = cards.length === items.length && cards.every(
            (card, index) => card.dataset.reorderKey === String(getKey(items[index]))
        );
        const nextSlots = new Map<string, { x: number; y: number }>();

        cards.forEach((card, index) => {
            const key = card.dataset.reorderKey!;
            const content = card.querySelector<HTMLElement>("[data-reorder-content]");
            const slot = slots[index];
            // React Aria may commit its DOM order after this parent's layout effect.
            // Once dragging ends, each card must occupy its own DOM slot regardless
            // of whether that separate collection commit has happened yet.
            const destination = isPreviewing ? destinations.get(key) ?? slot : slot;
            nextSlots.set(key, slot);
            if (!content) return;
            // Preserve the current visual position when an in-flight animation is interrupted,
            // including when the real collection adopts the preview order after dropping.
            const matrix = new DOMMatrixReadOnly(getComputedStyle(content).transform);
            const previous = previousSlots.current.get(key) ?? slot;
            const from = {x: previous.x + matrix.m41 - slot.x, y: previous.y + matrix.m42 - slot.y};
            const to = {x: destination.x - slot.x, y: destination.y - slot.y};
            animations.current.get(content)?.cancel();
            content.style.transform = `translate(${to.x}px, ${to.y}px)`;
            if (!reducedMotion && collectionCommitted && (Math.abs(from.x - to.x) > 0.5 || Math.abs(from.y - to.y) > 0.5)) {
                const animation = content.animate([
                    {transform: `translate(${from.x}px, ${from.y}px)`},
                    {transform: `translate(${to.x}px, ${to.y}px)`}
                ], {duration: 180, easing: "cubic-bezier(0.2, 0, 0, 1)"});
                animations.current.set(content, animation);
            } else {
                animations.current.delete(content);
            }
        });
        previousSlots.current = nextSlots;
        animations.current.forEach((animation, content) => {
            if (!container.contains(content)) {
                animation.cancel();
                animations.current.delete(content);
            }
        });
    });

    useLayoutEffect(() => () => {
        animations.current.forEach(animation => animation.cancel());
        animations.current.clear();
    }, []);

    return {ref, dragAndDropHooks};
}
