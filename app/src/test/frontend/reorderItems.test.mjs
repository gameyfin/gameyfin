import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {test} from "node:test";
import {runInThisContext} from "node:vm";
import ts from "typescript";

const exports = {};
const source = readFileSync(new URL("../../main/frontend/util/reorderItems.ts", import.meta.url), "utf8");
runInThisContext(`(function(exports) {${ts.transpileModule(source, {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
}).outputText}\n})`)(exports);
const items = ["a", "b", "c", "d"];
const reorder = (keys, key, dropPosition) => exports.reorderItems(
    items, new Set(keys), {type: "item", key, dropPosition}, item => item
);

test("preview and drop insert before and after targets in either direction", () => {
    assert.deepEqual(reorder(["a"], "c", "before"), ["b", "a", "c", "d"]);
    assert.deepEqual(reorder(["a"], "d", "after"), ["b", "c", "d", "a"]);
    assert.deepEqual(reorder(["d"], "a", "before"), ["d", "a", "b", "c"]);
    assert.deepEqual(reorder(["d"], "a", "after"), ["a", "d", "b", "c"]);
});

test("multi-item drags preserve collection order regardless of selection order", () => {
    assert.deepEqual(reorder(["c", "a"], "d", "after"), ["b", "d", "a", "c"]);
});

test("self drops, adjacent drops, and stale targets leave the order intact", () => {
    assert.deepEqual(reorder(["b"], "b", "before"), items);
    assert.deepEqual(reorder(["b"], "c", "before"), items);
    assert.deepEqual(reorder(["b"], "missing", "after"), items);
    assert.deepEqual(reorder([], "a", "after"), items);
});

test("previewing never mutates the source and supports numeric collection keys", () => {
    const original = Object.freeze([{id: 1}, {id: 2}, {id: 3}]);
    const preview = exports.reorderItems(original, new Set([1]),
        {type: "item", key: 3, dropPosition: "after"}, item => item.id);
    assert.deepEqual(preview.map(item => item.id), [2, 3, 1]);
    assert.deepEqual(original.map(item => item.id), [1, 2, 3]);
    assert.equal(preview[2], original[0]);
});
