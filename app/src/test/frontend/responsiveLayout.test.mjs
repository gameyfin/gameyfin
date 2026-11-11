import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {test} from "node:test";
import {runInThisContext} from "node:vm";
import ts from "typescript";

function load(relativePath) {
    const source = readFileSync(new URL(`../../main/frontend/${relativePath}`, import.meta.url), "utf8");
    const output = ts.transpileModule(source, {compilerOptions: {
        module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022
    }}).outputText;
    const exports = {};
    runInThisContext(`(function(exports) {${output}\n})`)(exports);
    return exports;
}
const {coverGridLayout, COVER_GAP} = load("util/coverGridLayout.ts");
const {navigationVisibility} = load("components/layout/navigationVisibility.ts");

test("phone grid fits two covers without sacrificing aspect ratio", () => {
    for (const width of [361, 398]) {
        const grid = coverGridLayout(width, true);
        assert.equal(grid.columnCount, 2);
        assert.ok(grid.actualColumnWidth >= 140);
        assert.ok(grid.columnCount * grid.actualColumnWidth + COVER_GAP <= width);
        assert.equal(grid.coverHeight, Math.floor(grid.actualColumnWidth * 17 / 12));
    }
});

test("grid retains desktop sizes and responds to container resize", () => {
    for (const narrow of [false, true]) {
        for (const width of [0, 1, 139, 140, 295, 296, 607, 608, 788, 1280, 1728, 1920]) {
            const grid = coverGridLayout(width, narrow);
            assert.ok(grid.columnCount >= 1);
            assert.ok(grid.actualColumnWidth >= 0 && grid.actualColumnWidth <= 212);
            assert.ok(grid.columnCount * grid.actualColumnWidth + (grid.columnCount - 1) * COVER_GAP <= width);
            if (!narrow && width >= 180) assert.ok(grid.actualColumnWidth >= 180);
        }
    }
    assert.equal(coverGridLayout(608, false).columnCount, 3);
    assert.equal(coverGridLayout(608, true).columnCount, 4);
});

test("navigation exposes only actions for the current identity", () => {
    assert.deepEqual(navigationVisibility(false, false), {
        profile:false, administration:false, tasks:false, signIn:true, signOut:false
    });
    assert.deepEqual(navigationVisibility(true, false), {
        profile:true, administration:false, tasks:false, signIn:false, signOut:true
    });
    assert.deepEqual(navigationVisibility(true, true), {
        profile:true, administration:true, tasks:true, signIn:false, signOut:true
    });
    assert.equal(navigationVisibility(false, true).administration, false);
});
