import assert from "node:assert/strict";
import {mkdtemp, rm, writeFile} from "node:fs/promises";
import {fileURLToPath} from "node:url";
import {resolve} from "node:path";
import {createServer} from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

const {chromium} = await import(process.env.PLAYWRIGHT_MODULE || "playwright");
const app = fileURLToPath(new URL("../../../", import.meta.url));
const fixture = await mkdtemp(resolve(app, "build/overview-selection-"));
let server, browser;
try {
    await writeFile(resolve(fixture, "index.html"), '<div id="root"></div><script type="module" src="./fixture.tsx"></script>');
    const aliases = {};
    async function stub(module, code) {
        const path = resolve(fixture, `stub-${Object.keys(aliases).length}.tsx`);
        await writeFile(path, code);
        aliases[`Frontend/${module}`] = path;
    }
    await stub("generated/endpoints", `
        const remove = kind => async id => {
            window.calls.push({kind, id});
            if (window.hold) await new Promise(resolve => window.finish = resolve);
            if (window.failId === id) throw new Error('Denied');
            return {id: kind + id};
        };
        export const LibraryEndpoint = {deleteLibrary: remove('library'), triggerScan: async () => window.scans++};
        export const CollectionEndpoint = {deleteCollection: remove('collection')};
    `);
    for (const [kind, names] of [["Library", ["Alpha", "Beta", "Gamma"]], ["Collection", ["Favorites", "Classics"]]]) {
        await stub(`state/${kind}State`, `import {proxy} from 'valtio';
            export const ${kind.toLowerCase()}State = proxy({sorted: ${JSON.stringify(names.map((name, i) => ({id: i + 1, name, platforms: [], metadata: {}})))}});`);
    }
    await stub("state/GameState", "import {proxy} from 'valtio'; export const gameState = proxy({randomlyOrderedGamesByLibraryId: {}});");
    await stub("state/PluginState", "import {proxy} from 'valtio'; export const pluginState = proxy({hasActiveMetadataPlugins: true});");
    await stub("state/derived", `export const useLibraryStats = () => undefined;
        export const useCollectionStats = () => undefined;
        export const useRandomlyOrderedGamesByCollectionId = () => ({});`);
    await stub("state/TaskState", "export const trackRun = run => window.tracked.push(run.id);");
    await stub("components/administration/withConfigPage", `import React from 'react';
        export default Component => () => <form onSubmit={e => {e.preventDefault(); window.submits++;}}>
            <Component getConfig={() => undefined} formik={{values: {library: {scan: {}}}}}/></form>;`);
    for (const module of ["components/administration/ConfigFormField", "components/general/Section",
        "components/general/modals/LibraryCreationModal", "components/general/modals/LibraryPrioritiesModal",
        "components/general/modals/CollectionCreationModal", "components/general/modals/CollectionPrioritiesModal",
        "components/general/modals/UploadGameButton", "components/general/IconBackgroundPattern"]) {
        await stub(module, "export default () => null;");
    }
    await writeFile(resolve(fixture, "fixture.tsx"), `
        import React from 'react';
        import {createRoot} from 'react-dom/client';
        import {MemoryRouter, useLocation} from 'react-router';
        import {GameManagement} from 'Frontend/components/administration/GameManagement';
        import 'Frontend/main.css';
        window.calls = []; window.tracked = []; window.scans = 0; window.submits = 0;
        function Location() { return <output>{useLocation().pathname}</output>; }
        createRoot(document.getElementById('root')).render(<MemoryRouter><GameManagement/><Location/></MemoryRouter>);
    `);
    server = await createServer({configFile: false, root: app, cacheDir: resolve(fixture, ".vite"),
        plugins: [react(), tailwindcss()], resolve: {alias: {...aliases, Frontend: resolve(app, "src/main/frontend")}},
        server: {host: "127.0.0.1", port: 0}});
    await server.listen();
    browser = await chromium.launch({headless: true,
        ...(process.env.CHROME_EXECUTABLE ? {executablePath: process.env.CHROME_EXECUTABLE} : {})});
    for (const touch of [false, true]) {
        const context = await browser.newContext({hasTouch: touch, viewport: {width: 1100, height: 900}});
        const page = await context.newPage();
        const errors = [];
        page.on("pageerror", error => errors.push(error.message));
        await page.goto(`${server.resolvedUrls.local[0]}build/${fixture.split("/").pop()}/index.html`);
        const activate = locator => touch ? locator.tap() : locator.click();
        const libraries = page.getByRole("grid", {name: "Libraries", exact: true});
        const collections = page.getByRole("grid", {name: "Collections", exact: true});
        const libraryDelete = page.getByRole("button", {name: "Delete selected"}).nth(0);
        const collectionDelete = page.getByRole("button", {name: "Delete selected"}).nth(1);
        const alpha = libraries.getByRole("row", {name: "Alpha", exact: true});
        const beta = libraries.getByRole("row", {name: "Beta", exact: true});
        const gamma = libraries.getByRole("row", {name: "Gamma", exact: true});
        await libraries.waitFor();
        assert.equal(await libraryDelete.isDisabled(), true);
        assert.equal(await collectionDelete.isDisabled(), true);
        await activate(alpha.getByText("Alpha", {exact: true}));
        await activate(beta.getByText("Beta", {exact: true}));
        assert.equal(await alpha.getAttribute("aria-selected"), "true");
        assert.equal(await beta.getAttribute("aria-selected"), "true");
        await activate(beta.getByText("Beta", {exact: true}));
        assert.equal(await beta.getAttribute("aria-selected"), "false");
        await beta.focus();
        await page.keyboard.press("Space");
        assert.equal(await beta.getAttribute("aria-selected"), "true");
        await activate(gamma.locator('button[aria-label="Scan library (quick)"]'));
        assert.equal(await gamma.getAttribute("aria-selected"), "false", "Scan must not select its card");
        assert.equal(await page.evaluate(() => window.scans), 1);
        await activate(gamma.locator('button[aria-label="Configuration"]'));
        assert.equal(await gamma.getAttribute("aria-selected"), "false", "Configuration must not select its card");
        await page.waitForFunction(() => document.querySelector("output").textContent === "/library/3");
        await activate(libraryDelete);
        const dialog = page.getByRole("dialog", {name: "Delete selected libraries"});
        await dialog.getByText("Alpha", {exact: true}).waitFor();
        await dialog.getByText("Beta", {exact: true}).waitFor();
        await activate(dialog.getByRole("button", {name: "Cancel", exact: true}));
        assert.deepEqual(await page.evaluate(() => window.calls), []);
        await page.evaluate(() => {window.failId = 2; window.hold = true;});
        await activate(libraryDelete);
        await activate(dialog.getByRole("button", {name: "Delete", exact: true}));
        await page.waitForFunction(() => Boolean(window.finish));
        assert.equal(await dialog.getByRole("button", {name: "Cancel", exact: true}).isDisabled(), true);
        await page.keyboard.press("Escape");
        assert.equal(await dialog.isVisible(), true);
        await page.evaluate(() => {window.hold = false; window.finish();});
        await dialog.waitFor({state: "hidden"});
        assert.deepEqual(await page.evaluate(() => window.calls), [{kind: "library", id: 1}, {kind: "library", id: 2}]);
        assert.deepEqual(await page.evaluate(() => window.tracked), ["library1"]);
        assert.equal(await alpha.getAttribute("aria-selected"), "false");
        assert.equal(await beta.getAttribute("aria-selected"), "true", "Failed requests stay selected");
        await page.evaluate(() => window.failId = undefined);
        await activate(libraryDelete);
        await activate(dialog.getByRole("button", {name: "Delete", exact: true}));
        await dialog.waitFor({state: "hidden"});
        assert.equal(await libraryDelete.isDisabled(), true);
        for (const name of ["Favorites", "Classics"]) await activate(collections.getByText(name, {exact: true}));
        assert.equal(await libraryDelete.isDisabled(), true, "Selections must be independent");
        await activate(collectionDelete);
        const collectionDialog = page.getByRole("dialog", {name: "Delete selected collections"});
        await collectionDialog.getByText("Favorites", {exact: true}).waitFor();
        await activate(collectionDialog.getByRole("button", {name: "Delete", exact: true}));
        await collectionDialog.waitFor({state: "hidden"});
        assert.equal(await collectionDelete.isDisabled(), true);
        assert.deepEqual(await page.evaluate(() => window.tracked), ["library1", "library2", "collection1", "collection2"]);
        assert.equal(await page.evaluate(() => window.submits), 0);
        assert.deepEqual(errors, []);
        await context.close();
    }
    console.log("Overview cards: mouse/touch/keyboard selection, action isolation, independent selections, confirmation/cancel, pending, partial failure/retry, task tracking passed.");
} finally {
    await browser?.close();
    await server?.close();
    await rm(fixture, {recursive: true, force: true});
}
