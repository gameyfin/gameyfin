import assert from "node:assert/strict";
import {mkdtempSync, readFileSync, rmSync} from "node:fs";
import {tmpdir} from "node:os";
import {join} from "node:path";
import {execFileSync} from "node:child_process";
import {after, test} from "node:test";
import {runInNewContext} from "node:vm";

// Use the installed native TypeScript compiler; TypeScript 7 has no transpileModule API.
const output = mkdtempSync(join(tmpdir(), "gameyfin-onboarding-"));
execFileSync(new URL("../../../node_modules/.bin/tsc", import.meta.url).pathname, [
    "--ignoreConfig", "--noCheck", "--module", "commonjs", "--jsx", "react-jsx", "--target", "es2022",
    "--outDir", output, new URL("../../main/frontend/views/PluginSetupView.tsx", import.meta.url).pathname
]);
const compiled = readFileSync(join(output, "PluginSetupView.js"), "utf8");
after(() => rmSync(output, {recursive: true, force: true}));

function fixture({role = "ROLE_SUPERADMIN", discovery = true, pending = true, finishError = false} = {}) {
    const values = [];
    let cursor;
    const effects = [];
    let elements;
    const window = {location: {href: "/setup/plugins"}};
    const calls = [];
    const errors = [];
    const jsx = (type, props) => { const el = {type, props}; elements.push(el); return el; };
    const dependencies = {
        "react/jsx-runtime": {jsx, jsxs: jsx},
        "react": {
            useState: initial => {
                const index = cursor++;
                if (!(index in values)) values[index] = initial;
                return [values[index], value => { values[index] = value; }];
            },
            useEffect: effect => effects.push(effect)
        },
        "@heroui/react": {Button: "Button", Spinner: "Spinner", toast: {danger: (...args) => errors.push(args)}},
        "Frontend/util/auth": {useAuth: () => ({state: {user: {roles: [role]}}})},
        "Frontend/generated/endpoints": {PluginOnboardingEndpoint: {
            isPending: async () => pending,
            isPluginDiscoveryComplete: async () => discovery,
            complete: async () => { calls.push("complete"); if (finishError) throw Error("offline"); }
        }},
        "Frontend/components/general/ActiveTaskPopover": {__esModule: true, default: "Progress"},
        "Frontend/components/general/plugin/marketplace/PluginMarketplace": {__esModule: true, default: "Marketplace"},
        "Frontend/components/general/plugin/PluginManagementSection": {PluginManagementSection: "Installed"}
    };
    const exports = {};
    runInNewContext(`(function(require, exports) {${compiled}\n})`, {
        window, setTimeout: () => 1, clearTimeout: () => {}
    })(name => { assert.ok(name in dependencies, name); return dependencies[name]; }, exports);
    return {
        window, calls, errors,
        render: () => { cursor = 0; elements = []; effects.length = 0; return exports.default(); },
        effects: async () => { effects.forEach(effect => effect()); await new Promise(resolve => setImmediate(resolve)); },
        has: type => elements.some(el => el.type === type),
        finish: async () => elements.find(el => el.type === "Button").props.onPress()
    };
}

test("fresh admin chooses individually and may finish with zero plugins", async () => {
    const f = fixture(); f.render(); await f.effects(); f.render();
    assert.ok(f.has("Marketplace")); assert.ok(f.has("Installed")); assert.ok(f.has("Progress"));
    await f.finish();
    assert.deepEqual(f.calls, ["complete"]); assert.equal(f.window.location.href, "/login/success");
});
test("discovery never prevents offline zero-plugin completion", async () => {
    const f = fixture({discovery: false}); f.render(); await f.effects(); f.render();
    assert.equal(f.has("Marketplace"), false); await f.finish();
    assert.equal(f.window.location.href, "/login/success");
});
test("ordinary users never see plugin installation or completion", async () => {
    const f = fixture({role: "ROLE_USER"}); f.render(); await f.effects(); f.render();
    assert.equal(f.has("Marketplace"), false); assert.equal(f.has("Button"), false); assert.deepEqual(f.calls, []);
});
test("pending setup resumes from server state after restart", async () => {
    const f = fixture(); f.render(); await f.effects(); f.render();
    assert.ok(f.has("Button")); assert.equal(f.window.location.href, "/setup/plugins");
});
test("completed setup returns through validated server destination", async () => {
    const f = fixture({pending: false}); f.render(); await f.effects();
    assert.equal(f.window.location.href, "/login/success");
});
test("failed completion remains retryable", async () => {
    const f = fixture({finishError: true}); f.render(); await f.effects(); f.render(); await f.finish();
    assert.equal(f.window.location.href, "/setup/plugins"); assert.equal(f.errors.length, 1);
    f.render(); assert.ok(f.has("Button"));
});
