import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {test} from "node:test";
import {runInNewContext} from "node:vm";
import ts from "typescript";

function fixture(pathname = "/") {
    const toasts = [];
    const window = {location: {pathname, href: pathname, search: "", hash: ""}};
    const loginExports = {};
    const loginSource = readFileSync(new URL("../../main/frontend/util/login.ts", import.meta.url), "utf8");
    const loginCompiled = ts.transpileModule(loginSource, {
        compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
    }).outputText;
    runInNewContext(`(function(exports) {${loginCompiled}\n})`, {window})(loginExports);
    const exports = {};
    const source = readFileSync(new URL("../../main/frontend/util/middleware.ts", import.meta.url), "utf8");
    const compiled = ts.transpileModule(source, {
        compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
    }).outputText;
    runInNewContext(`(function(require, exports) {${compiled}\n})`, {window})(
        specifier => specifier === "Frontend/util/login" ? loginExports :
            ({toast: {danger: (...args) => toasts.push(args)}}), exports);
    return {toasts, window, handle: response => exports.ErrorHandlingMiddleware(
        {endpoint: "CollectionEndpoint", method: "sync"}, async () => response)};
}

test("401 responses produce no toast and retain the login redirect", async () => {
    for (const pathname of ["/", "/login", "/setup", "/setup/", "/loginredirect"]) {
        const f = fixture(pathname);
        const response = new Response(JSON.stringify({message: "Login required"}), {status: 401});
        assert.equal(await f.handle(response), response);
        assert.deepEqual(f.toasts, []);
        assert.equal(f.window.location.href, pathname === "/" ? "/loginredirect?continue=%2F" : pathname);
        assert.deepEqual(await response.json(), {message: "Login required"});
    }
});

test("expired sessions preserve current page, query, and fragment", async () => {
    const f = fixture("/game/42");
    f.window.location.search = "?search=a%2Bb&sort=name";
    f.window.location.hash = "#details";
    await f.handle(new Response("", {status: 401}));
    assert.equal(f.window.location.href, "/loginredirect?continue=" +
        encodeURIComponent("/game/42?search=a%2Bb&sort=name#details"));
});

test("HTML forbidden responses redirect while JSON forbidden responses remain application errors", async () => {
    const html = fixture("/game/42");
    await html.handle(new Response("<html>Forbidden</html>", {status: 403}));
    assert.equal(html.window.location.href, "/loginredirect?continue=%2Fgame%2F42");
    const json = fixture("/game/42");
    await json.handle(new Response(JSON.stringify({message: "Forbidden"}), {status: 403}));
    assert.equal(json.window.location.href, "/game/42");
    assert.equal(json.toasts.length, 1);
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
