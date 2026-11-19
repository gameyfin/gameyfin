import assert from "node:assert/strict";
import {mkdtemp, readFile, rm, writeFile} from "node:fs/promises";
import {fileURLToPath} from "node:url";
import {resolve} from "node:path";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";
import {createServer} from "vite";

const playwright = await import(process.env.PLAYWRIGHT_MODULE || "playwright");
const engine = playwright[process.env.BROWSER_ENGINE || "chromium"];
const app = fileURLToPath(new URL("../../../", import.meta.url));
const fixture = await mkdtemp(resolve(app, "download-test-"));
const bytes = Buffer.from("Native file download contents");
const link = "magnet:?xt=urn:btih:1234&dn=Game%20Title&tr=http%3A%2F%2Ftracker.test%2Fannounce";
let server;
let browser;
let postedSelection;
try {
    await writeFile(resolve(fixture, "index.html"), `
        <script type="module" src="./fixture.tsx"></script>
        <button id="file">File</button><div id="modal"></div>
    `);
    await writeFile(resolve(fixture, "fixture.tsx"), `
        import React from "react";
        import "Frontend/main.css";
        import {createRoot} from "react-dom/client";
        import GameDownloadButtons from "Frontend/components/general/input/GameDownloadButtons";
        import {downloadGame} from "Frontend/endpoints/DownloadEndpoint";
        const root = createRoot(document.querySelector("#modal"));
        window.showGame = (id, directory, capable) => {
            window.providers = ["Legacy", ...(capable ? ["First", "Second"] : [])].map(key =>
                ({key, name: key, description: key, supportsFileSelection: key !== "Legacy"}));
            root.render(<GameDownloadButtons key={id} gameId={id} metadata={{directory, fileSize: 123}}/>);
        };
        window.opened = [];
        window.toasts = [];
        window.open = (...args) => window.opened.push(args);
        window.downloadGame = downloadGame;
        document.querySelector("#file").onclick = () => downloadGame(window.gameId, "example.Provider");
    `);
    await writeFile(resolve(fixture, "endpoints.ts"), 'export const DownloadProviderEndpoint = {getProviders: async () => window.providers};');
    await writeFile(resolve(fixture, "auth.ts"), 'export const getCsrfToken = () => "test-token";');
    await writeFile(resolve(fixture, "preferences.ts"), 'export const useUserPreferenceService = () => ({get: async () => "Second", set: async () => {}});');
    await writeFile(resolve(fixture, "toast.ts"), `
        export * from "real-heroui";
        export const toast = {
            info: (...args) => window.toasts.push(args),
            danger: (...args) => window.toasts.push(args),
        };
    `);
    server = await createServer({
        configFile: false,
        root: app,
        cacheDir: resolve(fixture, ".vite"),
        resolve: {alias: {
            "Frontend/generated/endpoints": resolve(fixture, "endpoints.ts"),
            "Frontend/util/auth": resolve(fixture, "auth.ts"),
            "Frontend/util/user-preference-service": resolve(fixture, "preferences.ts"),
            Frontend: resolve(app, "src/main/frontend"),
            "@heroui/react": resolve(fixture, "toast.ts"),
            "real-heroui": resolve(app, "node_modules/@heroui/react/dist/index.js"),
        }},
        server: {host: "127.0.0.1", port: 0, watch: {ignored: ["**/build/**", "**/target/**"]}},
        plugins: [react(), tailwindcss(), {name: "download-fixture", configureServer(vite) {
            vite.middlewares.use(async (request, response, next) => {
                if (!request.url.startsWith("/download/")) return next();
                if (request.url === "/download/1/contents") {
                    response.setHeader("Content-Type", "application/json");
                    response.setHeader("ETag", '"game-files"');
                    response.end(JSON.stringify([
                        {path: "base.bin", size: 0},
                        {path: "updates/one.bin", size: 1024},
                        {path: "updates/two.bin", size: 2048},
                    ]));
                } else if (request.url.startsWith("/download/1/selection?")) {
                    let body = "";
                    for await (const chunk of request) body += chunk;
                    postedSelection = Object.fromEntries(new URLSearchParams(body));
                    response.setHeader("Content-Disposition", 'attachment; filename="Game.zip"');
                    response.setHeader("Content-Type", "application/zip");
                    response.setHeader("X-Frame-Options", "SAMEORIGIN");
                    response.end(bytes);
                } else if (request.url.startsWith("/download/2?")) {
                    response.setHeader("Content-Type", "text/html");
                    response.setHeader("X-Frame-Options", "SAMEORIGIN");
                    response.setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'self'; base-uri 'none'");
                    response.end(`<a id="download-link" href="${link.replaceAll("&", "&amp;")}">Open link</a>`);
                } else if (request.url.startsWith("/download/4?")) {
                    response.statusCode = 500;
                    response.setHeader("X-Frame-Options", "DENY");
                    response.end("Download failed");
                } else {
                    response.setHeader("Content-Type", "application/octet-stream");
                    response.setHeader("Content-Disposition", 'attachment; filename="game.bin"');
                    response.setHeader("X-Frame-Options", "SAMEORIGIN");
                    response.write(bytes.subarray(0, 5));
                    setTimeout(() => response.end(bytes.subarray(5)), 100);
                }
            });
        }}],
    });
    await server.listen();
    browser = await engine.launch({headless: true, executablePath: process.env.BROWSER_EXECUTABLE});
    const page = await browser.newPage({acceptDownloads: true});
    await page.goto(`${server.resolvedUrls.local[0]}${fixture.split("/").pop()}/index.html`);
    await page.waitForFunction(() => typeof window.downloadGame === "function");
    const initialUrl = page.url();
    // The same provider can produce a file or a link; only its response controls the notice.
    const downloads = [];
    for (const id of [1, 3]) {
        await page.evaluate(id => window.gameId = id, id);
        const [download] = await Promise.all([
            page.waitForEvent("download", {timeout: 5000}),
            page.locator("#file").click(),
        ]);
        downloads.push(download);
    }
    for (const download of downloads) {
        assert.equal(download.suggestedFilename(), "game.bin");
        assert.deepEqual(await readFile(await download.path()), bytes);
    }
    assert.deepEqual(await page.evaluate(() => window.toasts), []);
    await page.evaluate(() => window.downloadGame(2, "example.Provider"));
    await page.waitForFunction(() => window.toasts.length === 1);
    assert.deepEqual(await page.evaluate(() => window.opened), [[link, "_top"]]);
    assert.equal(await page.evaluate(() => window.toasts[0][0]), "Link opened");
    assert.equal(page.url(), initialUrl);
    assert.equal(await page.locator("iframe").count(), 1);
    await page.evaluate(() => window.downloadGame(4, "example.Provider"));
    await page.waitForFunction(() => window.toasts.length === 2);
    assert.equal(await page.evaluate(() => window.toasts[1][0]), "Could not start download");
    assert.equal(await page.evaluate(() => window.opened.length), 1);
    const browse = page.getByRole("button", {name: "Browse contents"});
    for (const [id, directory, capable] of [[3, false, true], [2, true, false]]) {
        await page.evaluate(args => window.showGame(...args), [id, directory, capable]);
        await page.getByRole("button", {name: /Legacy|Second/}).first().waitFor();
        assert.equal(await browse.count(), 0);
    }
    await page.evaluate(() => window.showGame(1, true, true));
    for (const width of [375, 1280]) {
        await page.setViewportSize({width, height: 800});
        await browse.waitFor();
        const icon = await browse.boundingBox();
        const download = await page.getByRole("button", {name: /Second.*123 B/}).boundingBox();
        assert.equal(icon.width, icon.height);
        assert.equal(icon.height, download.height);
    }
    await browse.click();
    const dialog = page.getByRole("dialog");
    await dialog.getByRole("button", {name: "Download options"}).click();
    await page.getByRole("menuitemradio", {name: /Second/}).waitFor();
    assert.equal(await page.getByRole("menuitemradio", {name: /Legacy/}).count(), 0);
    await page.keyboard.press("Escape");
    const tree = page.getByRole("tree", {name: "Game files"});
    await tree.waitFor();
    const action = page.getByRole("button", {name: /Download selection Second/});
    assert.equal(await action.isDisabled(), true);
    const total = dialog.getByRole("status");
    const base = tree.getByRole("treeitem", {name: "base.bin 0 B", exact: true});
    const folder = tree.getByRole("treeitem", {name: /^updates/});
    await total.filter({hasText: "Selected size: 0 B"}).waitFor();
    await base.getByText("0 B", {exact: true}).waitFor();
    await folder.getByText("3.0 KiB", {exact: true}).waitFor();
    await base.click();
    await base.locator('[data-slot="checkbox"][data-selected=true]').waitFor();
    await dialog.getByRole("button", {name: /Download selection Second/, disabled: false}).waitFor();
    assert.equal(await total.textContent(), "Selected size: 0 B");
    await base.click();
    await base.focus();
    await page.keyboard.press("ArrowDown");
    await page.keyboard.press("Space");
    await total.filter({hasText: "Selected size: 3.0 KiB"}).waitFor();
    await page.keyboard.press("ArrowRight");
    const first = tree.getByRole("treeitem", {name: /^one\.bin/});
    const second = tree.getByRole("treeitem", {name: /^two\.bin/});
    await first.getByText("1.0 KiB", {exact: true}).waitFor();
    await second.getByText("2.0 KiB", {exact: true}).waitFor();
    // Click at the visible HeroUI control. The tree owns its pointer and keyboard interaction.
    const control = first.locator('[data-slot="checkbox-control"]');
    const bounds = await control.boundingBox();
    await page.mouse.click(bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
    await folder.locator('[data-slot="checkbox"][data-indeterminate=true]').waitFor();
    assert.equal(await folder.getAttribute("aria-checked"), "mixed");
    await total.filter({hasText: "Selected size: 2.0 KiB"}).waitFor();
    await page.mouse.move(0, 0);
    assert.notEqual(await first.evaluate(el => getComputedStyle(el).backgroundColor),
        await second.evaluate(el => getComputedStyle(el).backgroundColor));
    await second.click();
    await total.filter({hasText: "Selected size: 0 B"}).waitFor();
    assert.equal(await action.isDisabled(), true);
    await second.click();
    await total.filter({hasText: "Selected size: 2.0 KiB"}).waitFor();
    const [selection] = await Promise.all([page.waitForEvent("download"), action.click()]);
    assert.equal(selection.suggestedFilename(), "Game.zip");
    assert.deepEqual(postedSelection, {provider: "Second", files: '["updates/two.bin"]', _csrf: "test-token"});
    assert.equal(page.url(), initialUrl);
    await browse.click();
    await base.click();
    const folderBox = await folder.locator('[data-slot="checkbox-control"]').boundingBox();
    await page.mouse.click(folderBox.x + folderBox.width / 2, folderBox.y + folderBox.height / 2);
    await total.filter({hasText: "Selected size: 3.0 KiB"}).waitFor();
    await page.mouse.move(0, 0);
    const selectedColor = await base.evaluate(el => getComputedStyle(el).backgroundColor);
    await base.hover();
    assert.notEqual(await base.evaluate(el => getComputedStyle(el).backgroundColor), selectedColor);
    await Promise.all([page.waitForEvent("download"), action.click()]);
    assert.deepEqual(postedSelection, {provider: "Second", contentsETag: '"game-files"', _csrf: "test-token"});
    console.log("Selection tree, preferred provider, form submission and ZIP attachment passed.");
    console.log("Native streaming, repeated downloads, actual link results, and error notices passed.");
} finally {
    await browser?.close();
    await server?.close();
    await rm(fixture, {recursive: true, force: true});
}
