import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {test} from "node:test";
import {runInNewContext} from "node:vm";
import ts from "typescript";

function fixture(initialPath = "/setup") {
    let pathname = initialPath;
    let previousDependencies;
    const calls = [];
    const auth = {state: {initializing: false, loading: false}};
    const noop = () => {};
    const state = new Proxy({}, {get: (_, name) => (...args) => calls.push({name, args})});
    const dependencies = {
        "react": {
            useEffect: (effect, deps) => {
                if (!previousDependencies || deps.some((dep, index) => dep !== previousDependencies[index])) {
                    previousDependencies = deps;
                    effect();
                }
            }
        },
        "react/jsx-runtime": {jsx: noop, jsxs: noop},
        "react-router": {useLocation: () => ({pathname}), useNavigate: () => noop},
        "@phosphor-icons/react": {IconContext: {Provider: noop}},
        "@heroui/react": {Toast: {Provider: noop}},
        "next-themes": {},
        "./main.css": {},
        "Frontend/theming/themes": {
            injectThemeStyles: noop, applyAccentTheme: noop, getStoredAccentTheme: noop
        },
        "Frontend/util/auth": {useAuth: () => auth},
        "Frontend/util/utils": {isAdmin: () => false},
        "Frontend/util/routing": {},
        "Frontend/util/middleware": {},
        "Frontend/generated/connect-client.default": {}
    };
    const source = readFileSync(new URL("../../main/frontend/App.tsx", import.meta.url), "utf8");
    const compiled = ts.transpileModule(source, {
        compilerOptions: {
            module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX
        }
    }).outputText;
    const exports = {};
    runInNewContext(`(function(require, exports) {${compiled}\nexports.render = ViewWithAuth;})`, {
        window: {location: {get pathname() { return pathname; }}}
    })(specifier => {
        if (specifier.startsWith("Frontend/state/")) return state;
        assert.ok(specifier in dependencies, `Unexpected dependency: ${specifier}`);
        return dependencies[specifier];
    }, exports);
    return {
        calls,
        render: (path = pathname) => {
            pathname = path;
            exports.render();
        }
    };
}

test("setup does not start application synchronization that requires login", () => {
    for (const path of ["/setup", "/setup/"]) {
        const f = fixture(path);
        f.render();
        assert.deepEqual(f.calls, []);
    }
});

test("leaving setup starts synchronization even when the auth state is unchanged", () => {
    const f = fixture();
    f.render();
    f.calls.length = 0;
    f.render("/login");
    for (const name of ["initializeGameState", "initializeLibraryState", "initializeCollectionState"]) {
        const calls = f.calls.filter(call => call.name === name);
        assert.equal(calls.length, 1, `${name} should start after setup`);
        assert.deepEqual(Array.from(calls[0].args), ["anonymous:"]);
    }
});

test("normal route changes do not restart synchronization", () => {
    const f = fixture("/");
    f.render();
    assert.ok(f.calls.some(call => call.name === "initializeGameState"));
    f.calls.length = 0;
    f.render("/library/1");
    assert.deepEqual(f.calls, []);
});
