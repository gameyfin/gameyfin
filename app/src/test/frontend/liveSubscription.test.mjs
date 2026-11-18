import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {test} from "node:test";
import {runInThisContext} from "node:vm";
import ts from "typescript";

const source = readFileSync(new URL("../../main/frontend/state/sync/liveSubscription.ts", import.meta.url), "utf8");
const compiled = ts.transpileModule(source, {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
}).outputText;
const module = {};
runInThisContext(`(function(require, exports) {${compiled}\n})`)(() => ({
    ActionOnLostSubscription: {REMOVE: "remove", RESUBSCRIBE: "resubscribe"}
}), module);

function fixture(invalidates = false) {
    const pending = [];
    let state = {};
    const transport = {
        onNext(callback) {
            this.next = callback;
            return this;
        },
        onConnectionStateChange(callback) {
            this.connection = callback;
            return this;
        },
        onSubscriptionLost(callback) {
            this.lost = callback;
            return this;
        },
        onError(callback) {
            this.error = callback;
            return this;
        },
        cancel() {
            this.cancelled = true;
        }
    };
    const stop = module.liveSubscription({
        invalidates,
        subscribe: () => transport,
        fetch: () => new Promise(resolve => pending.push(resolve)),
        replace: value => {
            state = {...value};
        },
        apply: events => events.forEach(event => {
            if (event.user) state[event.id] = event.user;
            else delete state[event.id];
        })
    });
    return {
        transport, pending, stop, get state() {
            return state;
        }
    };
}

const settle = () => new Promise(resolve => setImmediate(resolve));

test("invalidation during snapshot requests another snapshot and explicit refresh remains disposable", async () => {
    const f = fixture(true);
    f.transport.next([{changed: true}]);
    f.pending.shift()({1: "old"});
    await settle();
    assert.equal(f.pending.length, 1);
    f.pending.shift()({1: "current"});
    await settle();
    assert.deepEqual(f.state, {1: "current"});
    const refresh = f.stop.refresh();
    assert.equal(f.pending.length, 1);
    f.stop();
    f.pending.shift()({1: "private"});
    await refresh;
    await f.stop.refresh();
    assert.deepEqual(f.state, {1: "current"});
    assert.equal(f.pending.length, 0);
});

test("buffers creation, changes and deletion while initial snapshot is in flight", async () => {
    const f = fixture();
    f.transport.next([{id: 1, user: "renamed"}, {id: 2}, {id: 3, user: "new"}]);
    f.pending.shift()({1: "old", 2: "deleted"});
    await settle();
    assert.deepEqual(f.state, {1: "renamed", 3: "new"});
    f.stop();
});

test("reconnect replaces the snapshot to recover missed deletions and role changes", async () => {
    const f = fixture();
    f.pending.shift()({1: "admin", 2: "old"});
    await settle();
    f.transport.connection({detail: {state: "connected"}});
    f.pending.shift()({1: "user"});
    await settle();
    assert.deepEqual(f.state, {1: "user"});
    f.stop();
});

test("account change ignores both stale snapshots and late subscription callbacks", async () => {
    const f = fixture();
    f.stop();
    f.pending.shift()({1: "private"});
    f.transport.next([{id: 2, user: "private"}]);
    await settle();
    assert.deepEqual(f.state, {});
    assert.equal(f.transport.cancelled, true);
    assert.equal(f.transport.lost(), "remove");
});

test("lost subscription resubscribes and reconciles from a fresh snapshot", async () => {
    const f = fixture();
    f.pending.shift()({1: "old"});
    await settle();
    assert.equal(f.transport.lost(), "resubscribe");
    f.transport.next([{id: 2, user: "new"}]);
    f.pending.shift()({});
    await settle();
    assert.deepEqual(f.state, {2: "new"});
    f.stop();
});
