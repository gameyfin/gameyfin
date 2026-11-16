import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {afterEach, mock, test} from "node:test";
import {runInThisContext} from "node:vm";
import ts from "typescript";

function load(path, dependencies) {
    const exports = {};
    const source = readFileSync(new URL(path, import.meta.url), "utf8");
    const compiled = ts.transpileModule(source, {
        compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
    }).outputText;
    runInThisContext(`(function(require,exports){${compiled}\n})`)(name => {
        assert.ok(name in dependencies, `Unexpected dependency: ${name}`);
        return dependencies[name];
    }, exports);
    return exports;
}

const ordering = load("../../main/frontend/util/mediaOrder.ts", {});
const media = load("../../main/frontend/util/gameMedia.ts", {
    "./mediaOrder": ordering,
    "Frontend/generated/endpoints": {GameEndpoint: {}},
    "Frontend/util/auth": {getCsrfToken: () => "csrf-test"}
});
const upload = load("../../main/frontend/util/gameUpload.ts", {
    "Frontend/util/auth": {getCsrfToken: () => "csrf-test"},
    "Frontend/util/gameMedia": media
});

afterEach(() => mock.restoreAll());

test("prefilled metadata keeps remote artwork and sends only user changes", () => {
    const values = upload.uploadMetadataValues({
        id: 0, title: "Provider title", summary: "Full description", platforms: [], genres: ["ACTION"],
        cover: {url: "https://example.com/cover.png"}, header: {url: "https://example.com/header.png"},
        images: [{url: "https://example.com/screenshot.png"}], videoUrls: ["https://example.com/video.mp4"],
        mediaOrder: ["video", "image"]
    }, 1, "/games/Original.iso");
    assert.equal(values.metadata.path, "/games/Original.iso");
    assert.equal(values.mediaCover.previewUrl, "https://example.com/cover.png");
    assert.deepEqual(values.mediaOrder, ["video", "image"]);
    assert.deepEqual(media.metadataUpdate(values, values), {id: 0});
    assert.deepEqual(media.metadataUpdate(values, {...values, title: "Edited"}), {id: 0, title: "Edited"});
    assert.deepEqual(media.metadataUpdate(values, {...values, mediaImages: []}).images, []);
});

test("upload transport includes CSRF, rejects expired sessions and reports server errors", async () => {
    mock.method(globalThis, "fetch", async (url, options) => {
        assert.equal(url, "/game-uploads/123/restart");
        assert.equal(options.headers["X-CSRF-Token"], "csrf-test");
        assert.equal(options.credentials, "same-origin");
        return new Response(null, {status: 204});
    });
    await upload.uploadRequest("/123/restart", {method: "POST"});
    globalThis.fetch.mock.mockImplementation(async () => new Response('{"message":"Name already exists"}', {status: 409}));
    await assert.rejects(upload.uploadRequest(""), /Name already exists/);
    globalThis.fetch.mock.mockImplementation(async () => ({redirected: true}));
    await assert.rejects(upload.uploadRequest(""), /session has expired/);
});

test("binary transfer sends File directly, reports bytes and supports cancellation", async () => {
    const original = globalThis.XMLHttpRequest;
    let request;
    globalThis.XMLHttpRequest = class {
        upload = {};
        headers = {};
        constructor() { request = this; }
        open(method, path) { this.method = method; this.path = path; }
        setRequestHeader(name, value) { this.headers[name] = value; }
        send(body) { this.body = body; }
        abort() { this.onabort(); }
    };
    try {
        const file = new File(["abc"], "Original.iso");
        const progress = [];
        const controller = new AbortController();
        const pending = upload.uploadFile("123", 2, file, controller.signal, bytes => progress.push(bytes));
        assert.equal(request.method, "PUT");
        assert.equal(request.path, "/game-uploads/123/files/2");
        assert.equal(request.body, file);
        assert.equal(request.headers["X-CSRF-Token"], "csrf-test");
        request.upload.onprogress({loaded: 2});
        assert.deepEqual(progress, [2]);
        controller.abort();
        await assert.rejects(pending, /cancelled/);
    } finally {
        globalThis.XMLHttpRequest = original;
    }
});

test("awaited resync populates new game summaries before navigation", async () => {
    const {createSyncedStore} = load("../../main/frontend/state/sync/createSyncedStore.ts", {
        "@vaadin/hilla-frontend": {ActionOnLostSubscription: {}},
        valtio: {snapshot: value => value}, react: {},
        "Frontend/generated/org/gameyfin/app/core/sync/EntityEventType": {default: {}},
        "Frontend/state/sync/EntityCache": {entityCache: {
            open: async () => {}, getAll: async () => [], getMeta: async () => undefined,
            clear: () => {}, put: () => {}, setMeta: () => {}, remove: () => {}
        }},
        "Frontend/state/sync/patches": {},
        "Frontend/state/sync/timestamps": {compareTimestamps: (a, b) => a.localeCompare(b)}
    });
    const subscription = {onNext() { return this; }, onConnectionStateChange() { return this; }, onSubscriptionLost() { return this; }};
    let release;
    let calls = 0;
    const state = {state: {}, full: {}};
    const store = createSyncedStore(state, {
        name: "games", summaryStore: "games", subscribe: () => subscription,
        fetchSync: async () => {
            if (++calls === 1) return {full: true, items: [], serverTime: "1"};
            return new Promise(resolve => { release = resolve; });
        }
    });
    await store.start("admin");
    const refreshing = store.resync();
    assert.equal(state.state[42], undefined);
    release({full: false, items: [{id: 42, title: "Uploaded", updatedAt: "2"}], allIds: [42], serverTime: "2"});
    await refreshing;
    assert.equal(state.state[42].title, "Uploaded");
});
