import assert from "node:assert/strict";
import {mkdtemp, mkdir, rm, writeFile} from "node:fs/promises";
import {fileURLToPath} from "node:url";
import {resolve} from "node:path";
import {createServer} from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

const {chromium} = await import(process.env.PLAYWRIGHT_MODULE || "playwright");
const app = fileURLToPath(new URL("../../../", import.meta.url));
await mkdir(resolve(app, "build"), {recursive: true});
const fixture = await mkdtemp(resolve(app, "build/upload-ui-"));
const artifacts = resolve(app, "build/upload-ui-review");
await mkdir(artifacts, {recursive: true});
let server;
let browser;
let page;
try {
    await writeFile(resolve(fixture, "index.html"), '<meta name="viewport" content="width=device-width, initial-scale=1"><div id="root"></div><script type="module" src="./fixture.tsx"></script>');
    await writeFile(resolve(fixture, "library.ts"), `
        import {proxy} from "valtio";
        export const libraryState = proxy({isLoaded: true, sorted: location.search.includes("empty") ? [] : [
            {id: 1, name: "PC games", platforms: ["PC_MICROSOFT_WINDOWS"],
             directories: [{internalPath: "/games/main"}, {internalPath: "/games/alternate"}]},
            {id: 2, name: "Console games", platforms: ["PLAYSTATION_5"],
             directories: [{internalPath: "/games/console"}]}
        ]});
        export const useFullLibrary = id => ({full: libraryState.sorted.find(library => library.id === id)});
    `);
    await writeFile(resolve(fixture, "config.ts"), `
        import {proxy} from "valtio";
        export const configState = proxy({isLoaded: true, config: {testSetting: "Initial"}, state: {}});
        export const initializeConfigState = async () => {};
    `);
    await writeFile(resolve(fixture, "games.ts"), `
        export const gameState = {state: {1: {id: 1}}};
        export const gameStore = {resync: async () => {}};
        export const useFullGame = () => ({});
    `);
    await writeFile(resolve(fixture, "plugins.ts"), `
        import {proxy} from "valtio";
        export const pluginState = proxy({state: Object.fromEntries(
            ["igdb", "steam", "gog", "screenshots"].map(id => [id, {id, name: id, hasLogo: false}])
        )});
    `);
    await writeFile(resolve(fixture, "auth.ts"), `
        export const useAuth = () => ({state: {user: {username: "Admin", roles: ["ROLE_ADMIN"]}}});
        export const getCsrfToken = () => "test-token";
    `);
    await writeFile(resolve(fixture, "endpoints.ts"), `
        export const PlatformEndpoint = {};
        export const ConfigEndpoint = {update: async value => {window.configSaves = [...(window.configSaves ?? []), value]}};
        export const GameEndpoint = {
            getEnumPropertyValues: async () => ({genres: [], themes: [], features: [], perspectives: []}),
            getPotentialMatches: async (term, platforms) => {
                window.platformFilters = [...(window.platformFilters ?? []), platforms];
                if (window.holdSearch) await new Promise(resolve => {window.releaseSearch = resolve});
                return ["A short title", "A much longer game title that should wrap without pushing sources aside"].map((title, i) => ({
                    id: String(i), title, release: "2025-01-01", developers: ["Example Studios"], publishers: ["Example Publishing"],
                    originalIds: Object.fromEntries(["igdb", "steam", "gog", "screenshots"].map(id => [id, {pluginId: id, externalProviderId: "1"}]))
                }));
            }
        };
    `);
    await writeFile(resolve(fixture, "fixture.tsx"), `
        import React from "react";
        import {createRoot} from "react-dom/client";
        import {MemoryRouter} from "react-router";
        import UploadGameButton from "Frontend/components/general/modals/UploadGameButton";
        import withConfigPage from "Frontend/components/administration/withConfigPage";
        import Wizard from "Frontend/components/wizard/Wizard";
        import WizardStep from "Frontend/components/wizard/WizardStep";
        import {Field} from "formik";
        import * as Yup from "yup";
        import "Frontend/main.css";
        const setup = <Wizard initialValues={{name: ""}} onSubmit={async values => {window.setupValues = values}}>
            <WizardStep title="Account" icon={<span>1</span>} validationSchema={Yup.object({name: Yup.string().required()})}>
                <Field name="name" aria-label="Setup name"/>
            </WizardStep>
            <WizardStep title="Finish" icon={<span>2</span>}><p>Ready to finish</p></WizardStep>
        </Wizard>;
        const Management = withConfigPage(({formik}) => <input aria-label="Test setting"
            value={formik.values.testSetting} onChange={event => formik.setFieldValue("testSetting", event.target.value)}/>,
            "Games", undefined, <UploadGameButton/>);
        createRoot(document.getElementById("root")).render(<MemoryRouter><main style={{padding:32}}>
            {location.search.includes("setup") ? setup : <Management/>}
        </main></MemoryRouter>);
    `);
    server = await createServer({
        configFile: false, root: app, cacheDir: resolve(fixture, ".vite"), plugins: [react(), tailwindcss()],
        resolve: {alias: {
            "Frontend/state/LibraryState": resolve(fixture, "library.ts"),
            "Frontend/state/GameState": resolve(fixture, "games.ts"),
            "Frontend/state/PluginState": resolve(fixture, "plugins.ts"),
            "Frontend/state/ConfigState": resolve(fixture, "config.ts"),
            "Frontend/generated/endpoints": resolve(fixture, "endpoints.ts"),
            "Frontend/util/auth": resolve(fixture, "auth.ts"),
            Frontend: resolve(app, "src/main/frontend")
        }}, server: {host: "127.0.0.1", port: 0}
    });
    await server.listen();
    browser = await chromium.launch({headless: true,
        ...(process.env.CHROME_EXECUTABLE ? {executablePath: process.env.CHROME_EXECUTABLE} : {})});
    page = await browser.newPage({viewport: {width: 1440, height: 1000}});
    const errors = [];
    const calls = [];
    let failComplete = true;
    let destinationConflict = true;
    page.on("pageerror", error => errors.push(error.message));
    await page.route("**/game-uploads{,/**}", async route => {
        const path = new URL(route.request().url()).pathname;
        calls.push({path, body: route.request().postData()});
        if (path === "/game-uploads/destination") {
            const destination = JSON.parse(route.request().postData());
            if (destinationConflict && destination.directory === "/games/alternate") {
                return route.fulfill({status: 409, json: {message: "A file or folder with this name already exists"}});
            }
            return route.fulfill({status: 204});
        }
        if (path.endsWith("/complete") && failComplete) {
            failComplete = false;
            return route.fulfill({status: 400, json: {message: "Storage unavailable"}});
        }
        if (path.endsWith("/metadata")) {
            const manual = Object.keys(JSON.parse(route.request().postData() || "{}")).length === 0;
            const manifest = JSON.parse(calls.filter(call => call.path === "/game-uploads").at(-1).body);
            return route.fulfill({json: {
                id: 0, title: manual ? manifest.name : "Prefilled game", platforms: [],
                summary: manual ? undefined : "Provider description"
            }});
        }
        if (path.endsWith("/restart") || path.includes("/files/") || route.request().method() === "DELETE") return route.fulfill({status: 204});
        return route.fulfill({json: {id: "test-session", gameId: path.endsWith("/complete") ? 1 : undefined, completedFiles: 0}});
    });
    await page.goto(`${server.resolvedUrls.local[0]}build/${fixture.split("/").pop()}/index.html`);
    assert.equal(await page.getByRole("button", {name: "Upload game", exact: true}).count(), 1);
    await page.getByRole("textbox", {name: "Test setting"}).fill("Changed");
    await page.getByRole("button", {name: "Upload game", exact: true}).click();
    const dialog = page.getByRole("dialog", {name: "Upload game"});
    assert.equal(await dialog.getByRole("button", {name: "Next step", exact: true}).isDisabled(), true);
    assert.equal(await dialog.getByRole("button", {name: "Destination", exact: true}).isDisabled(), true);
    await page.getByLabel("Choose game file", {exact: true}).setInputFiles({name: "Original game.iso", mimeType: "application/octet-stream", buffer: Buffer.from("game")});
    await dialog.getByRole("button", {name: "Next step", exact: true}).click();
    assert.equal(await dialog.getByRole("button", {name: "Next step", exact: true}).isDisabled(), true);
    await dialog.getByRole("button", {name: /Target library/}).click();
    await page.getByRole("option", {name: "PC games", exact: true}).click();
    await dialog.getByRole("button", {name: /Library directory/}).click();
    await page.getByRole("option", {name: "/games/alternate", exact: true}).click();
    await dialog.getByText("A file or folder with this name already exists", {exact: true}).waitFor();
    assert.equal(await dialog.getByRole("button", {name: "Next step", exact: true}).isDisabled(), true);
    assert.equal(await dialog.getByRole("button", {name: "Metadata", exact: true}).isDisabled(), true);
    assert.equal(calls.some(call => call.path === "/game-uploads"), false, "Preflight must not create an upload session");
    await page.screenshot({path: resolve(artifacts, "destination-conflict.png")});
    destinationConflict = false;
    await dialog.getByRole("button", {name: "Check again", exact: true}).click();
    await page.waitForFunction(() => [...document.querySelectorAll('button')].some(button => button.getAttribute('aria-label') === 'Next step' && !button.disabled));
    // A collision created after the automatic check must still block leaving Step 2.
    destinationConflict = true;
    await dialog.getByRole("button", {name: "Next step", exact: true}).click();
    await dialog.getByText("A file or folder with this name already exists", {exact: true}).waitFor();
    assert.equal(await dialog.getByRole("button", {name: "Destination", exact: true}).getAttribute("aria-current"), "step");
    destinationConflict = false;
    await dialog.getByRole("button", {name: "Check again", exact: true}).click();
    await dialog.getByRole("button", {name: "Next step", exact: true}).click();
    assert.equal(calls.filter(call => call.path !== "/game-uploads/destination").length, 0, "Steps must not upload or create a game before metadata review");
    await page.evaluate(() => {window.holdSearch = true});
    await dialog.getByRole("button", {name: "Search metadata", exact: true}).click();
    await page.waitForFunction(() => typeof window.releaseSearch === "function");
    assert.equal(await dialog.getByRole("button", {name: "Enter manually"}).isDisabled(), true);
    await page.evaluate(() => {window.releaseSearch()});
    await dialog.getByRole("grid", {name: "Search results"}).waitFor();
    const geometry = await dialog.locator("tbody tr").evaluateAll(rows => rows.map(row => {
        const cells = row.querySelectorAll("td");
        const sources = cells[3].firstElementChild;
        return {
            sourceWidth: sources.scrollWidth, cellWidth: cells[3].clientWidth,
            tops: [...sources.children].map(icon => icon.getBoundingClientRect().top),
            titleWidth: cells[0].clientWidth, tableWidth: row.clientWidth
        };
    }));
    assert.equal(geometry.length, 2);
    for (const row of geometry) {
        assert.equal(new Set(row.tops).size, 1, "Source icons must stay on one line");
        assert.ok(row.cellWidth >= row.sourceWidth, "Sources column must fit its icons");
        assert.ok(row.titleWidth < row.tableWidth * 0.5, "Title must leave room for other columns");
    }
    await page.screenshot({path: resolve(artifacts, "desktop-light.png")});
    await page.evaluate(() => {document.documentElement.classList.add("dark")});
    await page.waitForTimeout(250);
    await page.screenshot({path: resolve(artifacts, "desktop-dark.png")});
    await page.setViewportSize({width: 393, height: 852});
    await dialog.getByRole("region", {name: "Search results"}).waitFor();
    assert.equal(await dialog.getByRole("button", {name: "Use metadata for A short title"}).count(), 1);
    assert.ok(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth + 1), "Mobile dialog must not overflow");
    await page.screenshot({path: resolve(artifacts, "mobile-dark.png")});
    await dialog.getByRole("button", {name: "Use metadata for A short title"}).click();
    await page.locator('input[name="title"]').waitFor();
    assert.equal(await dialog.getByRole("button", {name: "Review", exact: true}).getAttribute("aria-current"), "step");
    assert.equal(await page.locator('input[name="title"]').inputValue(), "Prefilled game");
    await dialog.getByRole("button", {name: "Previous step", exact: true}).click();
    await dialog.getByRole("button", {name: "Enter manually", exact: true}).click();
    await page.locator('input[name="title"]').waitFor();
    assert.equal(await page.locator('input[name="title"]').inputValue(), "Original game.iso", "Manual entry must advance and clear the selected provider");
    assert.deepEqual(JSON.parse(calls.filter(call => call.path.endsWith("/metadata")).at(-1).body), {});
    await dialog.getByRole("button", {name: "Previous step", exact: true}).click();
    await dialog.getByRole("button", {name: "Use metadata for A short title"}).click();
    await page.locator('input[name="title"]').waitFor();
    await page.locator('input[name="title"]').fill("My edited title");
    const sessions = calls.filter(call => call.path === "/game-uploads").length;
    await dialog.getByRole("button", {name: "Previous step", exact: true}).click();
    await dialog.getByRole("button", {name: "Review metadata", exact: true}).click();
    assert.equal(await page.locator('input[name="title"]').inputValue(), "My edited title");
    assert.equal(calls.filter(call => call.path === "/game-uploads").length, sessions, "Back and Next must reuse the metadata draft");
    await dialog.getByRole("button", {name: "Destination", exact: true}).click();
    await dialog.getByRole("button", {name: /Target library/}).click();
    await page.getByRole("option", {name: "Console games", exact: true}).click();
    assert.equal(await dialog.getByRole("button", {name: /Library directory/}).count(), 0, "A library with one path selects it automatically");
    await dialog.getByRole("button", {name: "Next step", exact: true}).click();
    await page.evaluate(() => {window.holdSearch = false});
    await dialog.getByRole("button", {name: "Search metadata", exact: true}).click();
    await page.waitForFunction(() => window.platformFilters?.at(-1)?.[0] === "PLAYSTATION_5");
    await dialog.getByRole("button", {name: "Review metadata", exact: true}).click();
    assert.equal(await page.locator('input[name="title"]').inputValue(), "My edited title", "Changing library must preserve metadata edits");
    await dialog.getByRole("button", {name: "Destination", exact: true}).click();
    await dialog.getByRole("button", {name: /Target library/}).click();
    await page.getByRole("option", {name: "PC games", exact: true}).click();
    assert.equal(await dialog.getByRole("button", {name: "Next step", exact: true}).isDisabled(), true, "Changing library must clear the previous path");
    await dialog.getByRole("button", {name: /Library directory/}).click();
    await page.getByRole("option", {name: "/games/main", exact: true}).click();
    await dialog.getByRole("button", {name: "Next step", exact: true}).click();
    await dialog.getByRole("button", {name: "Review metadata", exact: true}).click();
    assert.equal(await page.locator('input[name="title"]').inputValue(), "My edited title", "Changing destination must preserve metadata edits");
    await dialog.getByRole("button", {name: "Files", exact: true}).click();
    await page.getByLabel("Choose game file", {exact: true}).setInputFiles({name: "Updated game.iso", mimeType: "application/octet-stream", buffer: Buffer.from("new game")});
    await dialog.getByRole("button", {name: "Next step", exact: true}).click();
    await dialog.getByRole("button", {name: "Next step", exact: true}).click();
    await dialog.getByRole("button", {name: "Review metadata", exact: true}).click();
    assert.equal(await page.locator('input[name="title"]').inputValue(), "My edited title", "Changing files must preserve metadata edits");
    await page.getByRole("button", {name: "Upload and save", exact: true}).click();
    await dialog.getByText("Storage unavailable", {exact: true}).waitFor();
    assert.equal(await page.locator('input[name="title"]').inputValue(), "My edited title");
    await page.getByRole("button", {name: "Upload and save", exact: true}).click();
    await page.waitForFunction(() => !document.querySelector('[role="dialog"]'));
    const manifest = JSON.parse(calls.filter(call => call.path === "/game-uploads").at(-1).body);
    assert.equal(manifest.directory, "/games/main");
    assert.equal(manifest.libraryId, 1);
    assert.equal(manifest.name, "Updated game.iso");
    assert.ok(calls.some(call => call.path.endsWith("/complete")));
    await page.getByRole("button", {name: "Upload game", exact: true}).click();
    assert.equal(await dialog.getByRole("button", {name: "Next step", exact: true}).isDisabled(), true, "Reopening resets the wizard");
    await page.getByLabel("Choose game file", {exact: true}).setInputFiles({name: "Manual.iso", mimeType: "application/octet-stream", buffer: Buffer.from("game")});
    await dialog.getByRole("button", {name: "Next step", exact: true}).click();
    await dialog.getByRole("button", {name: /Target library/}).click();
    await page.getByRole("option", {name: "PC games", exact: true}).click();
    await dialog.getByRole("button", {name: /Library directory/}).click();
    await page.getByRole("option", {name: "/games/main", exact: true}).click();
    await dialog.getByRole("button", {name: "Next step", exact: true}).click();
    await dialog.getByRole("button", {name: "Enter manually", exact: true}).click();
    await page.locator('input[name="title"]').waitFor();
    assert.equal(await page.locator('input[name="title"]').inputValue(), "Manual.iso", "Manual entry must open a fresh draft directly");
    await dialog.getByRole("button", {name: "Cancel", exact: true}).click();
    assert.equal(await page.evaluate(() => window.configSaves?.length ?? 0), 0, "Upload wizard submissions must not save configuration");
    await page.getByRole("button", {name: "Save", exact: true}).click();
    await page.waitForFunction(() => window.configSaves?.length === 1);
    assert.deepEqual(await page.evaluate(() => window.configSaves[0]), {updates: {testSetting: "Changed"}});
    await page.goto(`${server.resolvedUrls.local[0]}build/${fixture.split("/").pop()}/index.html?setup`);
    assert.equal(await page.getByRole("button", {name: "Finish", exact: true}).isDisabled(), true);
    await page.getByRole("button", {name: "Next step", exact: true}).click();
    assert.equal(await page.getByRole("button", {name: "Account", exact: true}).getAttribute("aria-current"), "step");
    await page.getByRole("textbox", {name: "Setup name"}).fill("Admin");
    await page.getByRole("button", {name: "Next step", exact: true}).click();
    await page.getByText("Ready to finish", {exact: true}).waitFor();
    await page.getByRole("button", {name: "Previous step", exact: true}).click();
    assert.equal(await page.getByRole("textbox", {name: "Setup name"}).inputValue(), "Admin");
    await page.getByRole("button", {name: "Next step", exact: true}).click();
    await page.getByRole("button", {name: "Finish setup", exact: true}).click();
    await page.waitForFunction(() => window.setupValues?.name === "Admin");
    assert.deepEqual(errors, []);
    console.log("Upload wizard passed: validation, back navigation, draft retention, retry, desktop/mobile, and setup compatibility.");
} catch (error) {
    if (page) {
        await page.screenshot({path: resolve(artifacts, "failure.png")}).catch(() => {});
        console.error(await page.locator("body").innerText().catch(() => "Page unavailable"));
    }
    throw error;
} finally {
    await browser?.close();
    await server?.close();
    await rm(fixture, {recursive: true, force: true});
}
