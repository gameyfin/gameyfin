import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {test} from "node:test";
import {runInNewContext} from "node:vm";
import ts from "typescript";

function fixture(pathname = "/") {
    const toasts = [];
    const window = {location: {pathname, href: pathname}};
    const exports = {};
    const source = readFileSync(new URL("../../main/frontend/util/middleware.ts", import.meta.url), "utf8");
    const compiled = ts.transpileModule(source, {
        compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
    }).outputText;
    runInNewContext(`(function(require, exports) {${compiled}\n})`, {window})(
        () => ({toast: {danger: (...args) => toasts.push(args)}}), exports);
    return {toasts, window, handle: response => exports.ErrorHandlingMiddleware(
        {endpoint: "CollectionEndpoint", method: "sync"}, async () => response)};
}

test("401 responses produce no toast and retain the login redirect", async () => {
    for (const pathname of ["/", "/login"]) {
        const f = fixture(pathname);
        const response = new Response(JSON.stringify({message: "Login required"}), {status: 401});
        assert.equal(await f.handle(response), response);
        assert.deepEqual(f.toasts, []);
        assert.equal(f.window.location.href, pathname === "/login" ? "/login" : "/loginredirect");
        assert.deepEqual(await response.json(), {message: "Login required"});
    }
});

test("successful public responses pass through without toasts or redirects", async () => {
    const f = fixture();
    const response = new Response("[]");
    assert.equal(await f.handle(response), response);
    assert.deepEqual(f.toasts, []);
    assert.equal(f.window.location.href, "/");
});

test("application errors still produce a toast", async () => {
    const f = fixture();
    const response = new Response(JSON.stringify({
        type: "com.vaadin.hilla.exception.EndpointException", message: "Unexpected failure"
    }), {status: 500});
    assert.equal(await f.handle(response), response);
    assert.equal(f.toasts.length, 1);
    assert.equal(f.toasts[0][0], "Error");
    assert.equal(f.toasts[0][1].description, "Unexpected failure");
});
