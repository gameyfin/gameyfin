import assert from "node:assert/strict";
import {mkdtemp, readFile, rm, writeFile} from "node:fs/promises";
import {fileURLToPath} from "node:url";
import {resolve} from "node:path";
import {createServer} from "vite";

const {chromium} = await import(process.env.PLAYWRIGHT_MODULE || "playwright");
const app = fileURLToPath(new URL("../../../", import.meta.url));
const fixture = await mkdtemp(resolve(app, "download-test-"));
const bytes = Buffer.from("Native file download contents");
const link = "magnet:?xt=urn:btih:1234&dn=Game%20Title&tr=http%3A%2F%2Ftracker.test%2Fannounce";
let server;
let browser;
try {
    await writeFile(resolve(fixture, "index.html"), '<script type="module" src="./fixture.ts"></script>');
    await writeFile(resolve(fixture, "fixture.ts"), `
        import {downloadGame} from "Frontend/endpoints/DownloadEndpoint";
        window.opened = [];
        window.toasts = [];
        window.open = (...args) => window.opened.push(args);
        window.downloadGame = downloadGame;
    `);
    await writeFile(resolve(fixture, "toast.ts"), `
        export const toast = {
            info: (...args) => window.toasts.push(args),
            danger: (...args) => window.toasts.push(args),
        };
    `);
    server = await createServer({
        configFile: false,
        root: app,
        cacheDir: resolve(fixture, ".vite"),
        resolve: {alias: {Frontend: resolve(app, "src/main/frontend"), "@heroui/react": resolve(fixture, "toast.ts")}},
        server: {host: "127.0.0.1", port: 0},
        plugins: [{name: "download-fixture", configureServer(vite) {
            vite.middlewares.use((request, response, next) => {
                if (!request.url.startsWith("/download/")) return next();
                if (request.url.startsWith("/download/2?")) {
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
                    response.setHeader("X-Frame-Options", "DENY");
                    response.write(bytes.subarray(0, 5));
                    setTimeout(() => response.end(bytes.subarray(5)), 100);
                }
            });
        }}],
    });
    await server.listen();
    browser = await chromium.launch({headless: true, executablePath: process.env.BROWSER_EXECUTABLE});
    const page = await browser.newPage({acceptDownloads: true});
    await page.goto(`${server.resolvedUrls.local[0]}${fixture.split("/").pop()}/index.html`);
    await page.waitForFunction(() => typeof window.downloadGame === "function");
    const initialUrl = page.url();
    // The same provider can produce a file or a link; only its response controls the notice.
    const downloads = [];
    for (const id of [1, 3]) {
        const pending = page.waitForEvent("download");
        await page.evaluate(id => window.downloadGame(id, "example.Provider"), id);
        downloads.push(await pending);
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
    console.log("Native streaming, repeated downloads, actual link results, and error notices passed.");
} finally {
    await browser?.close();
    await server?.close();
    await rm(fixture, {recursive: true, force: true});
}
