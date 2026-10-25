import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {test, afterEach, mock} from "node:test";
import {runInThisContext} from "node:vm";
import ts from "typescript";

// Exercise the real form/save helpers with Node's test runner and the existing TypeScript dependency.
// Only the Hilla transport and CSRF token are replaced; no frontend test framework is required.
const endpointCalls = [];
const source = readFileSync(new URL("../../main/frontend/util/gameMedia.ts", import.meta.url), "utf8");
const compiled = ts.transpileModule(source, {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
}).outputText;
const media = {};
const ordering = {};
const orderingSource = readFileSync(new URL("../../main/frontend/util/mediaOrder.ts", import.meta.url), "utf8");
runInThisContext(`(function(exports) {${ts.transpileModule(orderingSource, {
    compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022}
}).outputText}\n})`)(ordering);
runInThisContext(`(function(require, exports) {${compiled}\n})`)(specifier => {
    if (specifier === "./mediaOrder") return ordering;
    if (specifier === "Frontend/generated/endpoints") return {GameEndpoint: {updateGame: async update => { endpointCalls.push(update); }}};
    if (specifier === "Frontend/util/auth") return {getCsrfToken: () => "csrf-test"};
    throw new Error(`Unexpected runtime dependency: ${specifier}`);
}, media);

afterEach(() => { endpointCalls.length = 0; mock.restoreAll(); });

function game() {
    return {
        id: 1, title: "Game", platforms: [], metadata: {path: "/games/game"},
        cover: {id: 10}, header: {id: 20}, images: [{id: 31}, {id: 32}, {id: 33}],
        developers: ["First", "Second"],
        videoUrls: ["https://example.com/a.mp4", "https://example.com/b.mp4", "https://example.com/c.mp4"]
    };
}

test("form snapshots ignore subsequent live updates and reopening discards unsaved changes", () => {
    const live = game();
    const initial = media.metadataInitialValues(live);
    live.title = "Background update";
    live.images.reverse();
    assert.equal(initial.title, "Game");
    assert.deepEqual(initial.mediaImages.map(image => image.selection.id), [31, 32, 33]);
    const edited = {...initial, mediaImages: [], mediaVideos: [], title: "Unsaved"};
    assert.ok(media.metadataUpdate(initial, edited).images);
    const reopened = media.metadataInitialValues(live);
    assert.equal(reopened.title, "Background update");
    assert.equal(reopened.mediaImages.length, 3);
    assert.equal(endpointCalls.length, 0);
});

test("reordering submits complete lists including unchanged entries", () => {
    const initial = media.metadataInitialValues(game());
    const changed = media.metadataUpdate(initial, {
        ...initial,
        developers: ["First", "Changed"],
        mediaImages: [initial.mediaImages[1], initial.mediaImages[0], initial.mediaImages[2]],
        mediaVideos: [initial.mediaVideos[1], initial.mediaVideos[0], initial.mediaVideos[2]]
    });
    assert.deepEqual(changed.images, [{id: 32}, {id: 31}, {id: 33}]);
    assert.deepEqual(changed.videoUrls, ["https://example.com/b.mp4", "https://example.com/a.mp4", "https://example.com/c.mp4"]);
    assert.deepEqual(changed.developers, ["First", "Changed"]);
});

test("empty media lists clear media and an unchanged form only submits its ID", () => {
    const initial = media.metadataInitialValues(game());
    assert.deepEqual(media.metadataUpdate(initial, initial), {id: 1});
    assert.deepEqual(media.metadataUpdate(initial, {...initial, mediaImages: [], mediaVideos: []}), {id: 1, images: [], videoUrls: [], mediaOrder: []});
});

test("gallery defaults to screenshots first and preserves a saved mixed order on reopening", () => {
    const initial = media.metadataInitialValues(game());
    const imageKeys = initial.mediaImages.map(image => image.key);
    assert.deepEqual(ordering.orderMedia(imageKeys, initial.mediaVideos), [...imageKeys, ...initial.mediaVideos]);
    const mediaOrder = ["image", "image", "video", "image", "video", "video"];
    const update = media.metadataUpdate(initial, {...initial, mediaOrder});
    assert.deepEqual(update, {id: 1, mediaOrder});
    const reopened = media.metadataInitialValues({...game(), mediaOrder});
    assert.deepEqual(ordering.orderMedia(imageKeys, reopened.mediaVideos, reopened.mediaOrder), [
        imageKeys[0], imageKeys[1], initial.mediaVideos[0], imageKeys[2], initial.mediaVideos[1], initial.mediaVideos[2]
    ]);
    assert.deepEqual(media.metadataUpdate(reopened, reopened), {id: 1});
});

test("gallery tolerates removed media and appends new media without losing entries", () => {
    assert.deepEqual(ordering.orderMedia(["a", "b"], ["v"], ["video", "image"]), ["v", "a", "b"]);
    assert.deepEqual(ordering.orderMedia([], ["v"], ["image", "video", "image"]), ["v"]);
    assert.deepEqual(ordering.orderMedia(["a"], [], ["video", "image"]), ["a"]);
});

test("saving without uploads uses the existing Hilla endpoint", async () => {
    const initial = media.metadataInitialValues(game());
    const update = {id: 1, images: [{id: 32}, {id: 31}]};
    await media.saveMetadata(update, initial);
    assert.deepEqual(endpointCalls, [update]);
});

test("uploaded files and metadata share one multipart save with CSRF protection", async () => {
    const initial = media.metadataInitialValues(game());
    const cover = {key: "upload-test", previewUrl: "blob:preview", selection: {uploadKey: "upload-test"}, file: new File(["image"], "cover.png", {type: "image/png"})};
    const values = {...initial, mediaCover: cover};
    const update = media.metadataUpdate(initial, values);
    const transport = mock.method(globalThis, "fetch", async (url, options) => {
        assert.equal(url, "game-media/metadata");
        assert.equal(options.headers["X-CSRF-Token"], "csrf-test");
        assert.equal(options.credentials, "same-origin");
        assert.deepEqual(await options.body.get("metadata").text().then(JSON.parse), {id: 1, cover: {uploadKey: "upload-test"}});
        assert.equal(options.body.get("upload-test").name, "cover.png");
        assert.deepEqual([...options.body.keys()], ["metadata", "upload-test"]);
        return new Response(null, {status: 204});
    });
    await media.saveMetadata(update, values);
    assert.equal(transport.mock.callCount(), 1);
    assert.equal(endpointCalls.length, 0);
});

test("failed saves preserve drafts for retry and show server errors", async () => {
    const initial = media.metadataInitialValues(game());
    const file = new File(["image"], "cover.png", {type: "image/png"});
    const values = {...initial, mediaCover: {key: "upload-test", previewUrl: "blob:preview", selection: {uploadKey: "upload-test"}, file}};
    const update = media.metadataUpdate(initial, values);
    const transport = mock.method(globalThis, "fetch", async () => new Response(JSON.stringify({detail: "Invalid image"}), {status: 400}));
    await assert.rejects(media.saveMetadata(update, values), /Invalid image/);
    assert.equal(values.mediaCover.file, file);
    transport.mock.mockImplementation(async () => new Response(null, {status: 204}));
    await media.saveMetadata(update, values);
    assert.equal(transport.mock.callCount(), 2);
});

test("reject non-HTTP image URLs and excessive uploads", async () => {
    assert.throws(() => media.remoteImage("javascript:alert(1)"), /HTTP/);
    assert.equal(media.remoteImage(" https://example.com/image.png ").selection.url, "https://example.com/image.png");
    const initial = media.metadataInitialValues(game());
    const values = {...initial, mediaCover: {key: "oversized", selection: {uploadKey: "oversized"}, file: {size: media.MAX_MEDIA_SIZE + 1}}};
    const transport = mock.method(globalThis, "fetch", async () => { throw new Error("must not upload"); });
    await assert.rejects(media.saveMetadata({id: 1}, values), /100 MiB/);
    assert.equal(transport.mock.callCount(), 0);
});
