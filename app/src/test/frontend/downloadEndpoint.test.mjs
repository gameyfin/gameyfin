import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {test} from "node:test";
import {runInNewContext} from "node:vm";
import ts from "@typescript/typescript6";

function fixture() {
    const opened = [];
    const toasts = [];
    const frames = [];
    const exports = {};
    const source = readFileSync(new URL("../../main/frontend/endpoints/DownloadEndpoint.ts", import.meta.url), "utf8");
    const compiled = ts.transpileModule(source, {
        compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
    }).outputText;
    runInNewContext(`(function(require, exports) {${compiled}\n})`, {
        window: {open: (...args) => opened.push(args)},
        document: {
            createElement: tag => {
                assert.equal(tag, "iframe");
                return {contentDocument: {URL: "about:blank"}};
            },
            body: {appendChild: frame => frames.push(frame)},
        },
    })(name => {
        assert.equal(name, "@heroui/react");
        return {toast: {
            info: (...args) => toasts.push(args),
            danger: (...args) => toasts.push(args),
        }};
    }, exports);
    return {downloadGame: exports.downloadGame, opened, toasts, frames};
}

test("link downloads open normally and explain that a compatible client may be needed", () => {
    const f = fixture();
    f.downloadGame(42, "example.Provider");
    assert.equal(f.frames[0].src, "/download/42?provider=example.Provider&client=browser");
    assert.equal(f.toasts.length, 0);
    assert.equal(f.opened.length, 0);
    f.frames[0].contentDocument = {
        URL: "https://gameyfin.test/download/42",
        querySelector: () => ({href: "custom-client:game/42"}),
    };
    f.frames[0].onload();
    assert.deepEqual(f.opened, [["custom-client:game/42", "_top"]]);
    assert.equal(f.frames[0].src, "about:blank");
    assert.equal(f.toasts.length, 1);
    assert.equal(f.toasts[0][0], "Link opened");
    assert.equal(f.toasts[0][1].description,
        "If nothing happens, you may need to install a compatible client to open this type of link.");
});

test("file downloads stay native, reuse a frame and show no link toast", () => {
    const f = fixture();
    f.downloadGame(42, "example.Provider");
    f.frames[0].onload(); // Initial blank frame; attachments do not load an HTML document.
    f.downloadGame(43, "provider with & special chars");
    assert.equal(f.frames.length, 1);
    assert.equal(f.frames[0].src, "/download/43?provider=provider%20with%20%26%20special%20chars&client=browser");
    assert.equal(f.opened.length, 0);
    assert.equal(f.toasts.length, 0);
});

test("an error document does not open a link or show a misleading success toast", () => {
    const f = fixture();
    f.downloadGame(42, "example.Provider");
    f.frames[0].contentDocument = {URL: "https://gameyfin.test/download/42", querySelector: () => null};
    f.frames[0].onload();
    assert.equal(f.opened.length, 0);
    assert.equal(f.toasts[0][0], "Could not start download");
});
