import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {test} from "node:test";
import {runInThisContext} from "node:vm";
import ts from "typescript";

function loadModule(name, require = () => { throw new Error("Unexpected dependency"); }) {
    const source = readFileSync(new URL(`../../main/frontend/util/${name}.ts`, import.meta.url), "utf8");
    const exports = {};
    runInThisContext(`(function(require, exports) {${ts.transpileModule(source, {
        compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
    }).outputText}\n})`)(require, exports);
    return exports;
}

const ordering = loadModule("reorderItems");

// Model the collection's separate DOM commit: React Aria can move its keyed rows
// after the parent's layout effect has already run with the newly ordered items.
function setup(t, reducedMotion = true) {
    for (const [key, value] of Object.entries({
        window: {matchMedia: () => ({matches: reducedMotion})},
        getComputedStyle: content => ({transform: content.style.transform || "none"}),
        DOMMatrixReadOnly: class {
            constructor(transform) {
                const values = [...transform.matchAll(/-?\d+(?:\.\d+)?/g)].map(match => Number(match[0]));
                this.m41 = values[0] ?? 0;
                this.m42 = values[1] ?? 0;
            }
        }
    })) {
        const previous = Object.getOwnPropertyDescriptor(globalThis, key);
        Object.defineProperty(globalThis, key, {value, configurable: true, writable: true});
        t.after(() => previous ? Object.defineProperty(globalThis, key, previous) : delete globalThis[key]);
    }

    const slots = [{left: 0, top: 0}, {left: 100, top: 0}, {left: 0, top: 100}];
    const cards = ["a", "b", "c"].map(key => {
        const content = {style: {}, animations: [], animate(frames) {
            const animation = {frames, cancelled: false, cancel() { this.cancelled = true; }};
            this.animations.push(animation);
            return animation;
        }};
        const card = {dataset: {reorderKey: key}, content, querySelector: () => content,
            getBoundingClientRect: () => slots[container.cards.indexOf(card)]};
        return card;
    });
    const container = {
        cards: [...cards], scrollLeft: 0, scrollTop: 0,
        getBoundingClientRect: () => ({left: 0, top: 0}),
        querySelectorAll() { return this.cards; },
        contains: () => true
    };
    const state = [];
    let cursor, effects, options, items = ["a", "b", "c"];
    const react = {
        createElement: () => null,
        useRef(initial) { const index = cursor++; return state[index] ??= {current: initial}; },
        useState(initial) {
            const index = cursor++;
            if (!(index in state)) state[index] = initial;
            return [state[index], value => { state[index] = value; }];
        },
        useLayoutEffect(effect, deps) { if (!deps) effects.push(effect); }
    };
    const {useAnimatedReorder} = loadModule("useAnimatedReorder", name => {
        if (name === "react") return react;
        if (name === "react-aria-components") return {useDragAndDrop(value) { options = value; return {dragAndDropHooks: {}}; }};
        if (name === "./reorderItems") return ordering;
        throw new Error(`Unexpected dependency: ${name}`);
    });
    function render() {
        cursor = 0;
        effects = [];
        const result = useAnimatedReorder({items, getKey: item => item, getLabel: item => item,
            onReorder: reordered => { items = reordered; }});
        result.ref.current = container;
        effects.forEach(effect => effect());
    }
    render();
    const target = {type: "item", key: "c", dropPosition: "after"};
    options.onDragStart({keys: new Set(["a"])});
    options.onDropEnter({target});
    render();
    assert.equal(cards[0].content.style.transform, "translate(0px, 100px)");
    return {
        cards, container, render,
        drop() { options.onReorder({keys: new Set(["a"]), target}); options.onDragEnd(); },
        cancel() { options.onDragEnd(); }
    };
}

test("dropping clears preview offsets even before the collection commits its DOM order", t => {
    const ui = setup(t);
    ui.drop();
    ui.render();
    ui.cards.forEach(card => assert.equal(card.content.style.transform, "translate(0px, 0px)"));
    // The collection commits independently, without another parent render.
    ui.container.cards = [ui.cards[1], ui.cards[2], ui.cards[0]];
    ui.cards.forEach(card => assert.equal(card.content.style.transform, "translate(0px, 0px)"));
});

test("dropping also clears offsets when the DOM has already adopted the new order", t => {
    const ui = setup(t);
    ui.drop();
    ui.container.cards = [ui.cards[1], ui.cards[2], ui.cards[0]];
    ui.render();
    ui.cards.forEach(card => assert.equal(card.content.style.transform, "translate(0px, 0px)"));
});

test("a delayed collection commit cancels preview animations without animating toward stale slots", t => {
    const ui = setup(t, false);
    const previewAnimations = ui.cards.flatMap(card => card.content.animations);
    assert.equal(previewAnimations.length, 3);
    ui.drop();
    ui.render();
    ui.cards.forEach(card => assert.equal(card.content.style.transform, "translate(0px, 0px)"));
    assert.ok(previewAnimations.every(animation => animation.cancelled));
    assert.equal(ui.cards.flatMap(card => card.content.animations).length, 3);
});

test("cancelling restores all cards to their original slots", t => {
    const ui = setup(t);
    ui.cancel();
    ui.render();
    ui.cards.forEach(card => assert.equal(card.content.style.transform, "translate(0px, 0px)"));
});
