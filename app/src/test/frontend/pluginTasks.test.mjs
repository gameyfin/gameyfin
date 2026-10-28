import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {test} from "node:test";
import {runInThisContext} from "node:vm";
import ts from "typescript";
import * as valtio from "valtio/vanilla";

function loadModule(path, dependencies) {
    const source = readFileSync(new URL(path, import.meta.url), "utf8");
    const compiled = ts.transpileModule(source, {
        compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
    }).outputText;
    const exports = {};
    runInThisContext(`(function(require, exports) {${compiled}\n})`)(specifier => {
        assert.ok(specifier in dependencies, `Unexpected dependency: ${specifier}`);
        return dependencies[specifier];
    }, exports);
    return exports;
}

function fixture() {
    const notifications = [];
    const toast = Object.fromEntries(["success", "warning", "danger"].map(level =>
        [level, (...args) => notifications.push({level, args})]));
    let receive;
    let reloads = 0;
    const endpoints = {
        TaskEndpoint: {subscribeToTaskRuns: () => ({onNext: handler => { receive = handler; }})},
        PluginMarketplaceEndpoint: {
            install: async () => run(),
            update: async () => run(),
            uninstall: async () => run(),
            getPlugins: async () => { reloads++; return [{id: "plugin", status: "INSTALLED"}]; }
        }
    };
    const tasks = loadModule("../../main/frontend/state/TaskState.ts", {
        "valtio": valtio,
        "@heroui/react": {toast},
        "Frontend/generated/endpoints": endpoints,
        "Frontend/generated/org/gameyfin/app/core/tasks/TaskRunStatus": {
            __esModule: true,
            default: {IN_PROGRESS: "IN_PROGRESS", COMPLETED: "COMPLETED", FAILED: "FAILED", CANCELLED: "CANCELLED"}
        }
    });
    tasks.initializeTaskState();
    const marketplace = loadModule("../../main/frontend/state/MarketplaceState.ts", {
        "valtio/index": valtio,
        "@heroui/react": {toast},
        "Frontend/generated/endpoints": endpoints,
        "Frontend/generated/org/gameyfin/app/core/plugins/marketplace/dto/MarketplacePluginStatus": {
            __esModule: true,
            default: {UPDATE_AVAILABLE: "UPDATE_AVAILABLE"}
        },
        "Frontend/state/TaskState": tasks
    });
    return {marketplace, tasks, endpoints, notifications, receive: (...args) => receive(...args), reloads: () => reloads};
}

function run(status = "IN_PROGRESS") {
    return {id: "run", taskKey: "core.plugin.install", displayName: "Install plugin", status,
        visibleInOverview: false, summary: "Installed", errorMessage: "Download failed"};
}

const flush = () => new Promise(resolve => setImmediate(resolve));

for (const operation of ["installPlugin", "updatePlugin", "uninstallPlugin"]) {
    test(`${operation} returns promptly and keeps controls pending until completion`, async () => {
        const f = fixture();
        const result = await (operation === "installPlugin"
            ? f.marketplace[operation]("market", "plugin") : f.marketplace[operation]("plugin"));
        assert.equal(result.id, "run");
        assert.equal(f.marketplace.marketplaceState.pending.plugin, true);
        assert.equal(f.reloads(), 0);
        f.receive([run("COMPLETED")]);
        await flush();
        assert.equal(f.marketplace.marketplaceState.pending.plugin, undefined);
        assert.equal(f.reloads(), 1);
        assert.equal(f.notifications[0].level, "success");
    });
}

test("a failed background operation clears pending state and reports the failure", async () => {
    const f = fixture();
    await f.marketplace.installPlugin("market", "plugin");
    f.receive([run("FAILED")]);
    await flush();
    assert.equal(f.marketplace.marketplaceState.pending.plugin, undefined);
    assert.equal(f.notifications[0].level, "danger");
    assert.equal(f.notifications[0].args[1].description, "Download failed");
});

test("operations that finish before the response are announced and refresh the marketplace", async () => {
    const f = fixture();
    f.endpoints.PluginMarketplaceEndpoint.install = async () => run("COMPLETED");
    await f.marketplace.installPlugin("market", "plugin");
    await flush();
    assert.equal(f.marketplace.marketplaceState.pending.plugin, undefined);
    assert.equal(f.reloads(), 1);
    assert.equal(f.notifications.length, 1);
    assert.equal(f.notifications[0].level, "success");
});

test("a finished subscription update before the response is announced once", async () => {
    const f = fixture();
    f.endpoints.PluginMarketplaceEndpoint.install = async () => {
        f.receive([run("COMPLETED")]);
        return run();
    };
    await f.marketplace.installPlugin("market", "plugin");
    await flush();
    assert.equal(f.marketplace.marketplaceState.pending.plugin, undefined);
    assert.equal(f.notifications.length, 1);
});

test("failure to start the task clears pending state and propagates the error", async () => {
    const f = fixture();
    f.endpoints.PluginMarketplaceEndpoint.install = async () => { throw new Error("Unavailable"); };
    await assert.rejects(f.marketplace.installPlugin("market", "plugin"), /Unavailable/);
    assert.equal(f.marketplace.marketplaceState.pending.plugin, undefined);
});
