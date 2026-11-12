import assert from "node:assert/strict";
import {mkdtemp, writeFile, rm} from "node:fs/promises";
import {fileURLToPath} from "node:url";
import {resolve} from "node:path";
import {createServer} from "vite";
import react from "@vitejs/plugin-react";

const {chromium} = await import(process.env.PLAYWRIGHT_MODULE || "playwright");
const app = fileURLToPath(new URL("../../../", import.meta.url));
const fixture = await mkdtemp(resolve(app, "popup-test-"));
let server;
let browser;
try {
    await writeFile(resolve(fixture, "index.html"), '<div id="root"></div><script type="module" src="./fixture.tsx"></script>');
    await writeFile(resolve(fixture, "fixture.tsx"), `
        import React from "react";
        import {createRoot} from "react-dom/client";
        import InfoPopup from "Frontend/components/administration/InfoPopup";
        import ChipList from "Frontend/components/general/ChipList";
        import PluginIcon from "Frontend/components/general/plugin/PluginIcon";
        window.submits = 0;
        createRoot(document.getElementById("root")).render(<>
            <form onSubmit={event => {event.preventDefault(); window.submits++;}}>
                <InfoPopup content={"**Help text** with [a link](#help)"}/>
            </form>
            <ChipList items={["Visible", "Hidden item"]}/>
            <PluginIcon plugin={{id: "test", name: "Test plugin"}}/>
            <button>Outside</button>
        </>);
    `);
    server = await createServer({
        configFile: false,
        root: app,
        cacheDir: resolve(fixture, ".vite"),
        plugins: [react()],
        resolve: {alias: {Frontend: resolve(app, "src/main/frontend")}},
        server: {host: "127.0.0.1", port: 0}
    });
    await server.listen();
    browser = await chromium.launch({headless: true});
    for (const touch of [false, true]) {
        const context = await browser.newContext({hasTouch: touch, viewport: {width: 393, height: 852}});
        const page = await context.newPage();
        const errors = [];
        page.on("pageerror", error => errors.push(error.message));
        await page.goto(`${server.resolvedUrls.local[0]}${fixture.split("/").pop()}/index.html`);
        const info = page.getByRole("button", {name: "More information"});
        const dialog = page.getByRole("dialog", {name: "Details"});
        const activate = locator => touch ? locator.tap() : locator.click();
        await activate(info);
        await dialog.waitFor();
        assert.equal(await dialog.locator("strong").textContent(), "Help text");
        assert.equal(await dialog.getByRole("link", {name: "a link"}).getAttribute("href"), "#help");
        await page.keyboard.press("Escape");
        await dialog.waitFor({state: "hidden"});
        assert.equal(await info.evaluate(element => element === document.activeElement), true);
        await activate(info);
        await dialog.waitFor();
        if (touch) await page.touchscreen.tap(380, 800);
        else await page.mouse.click(380, 800);
        await dialog.waitFor({state: "hidden"});
        for (const key of ["Enter", "Space"]) {
            await info.focus();
            await page.keyboard.press(key);
            await dialog.waitFor();
            await page.keyboard.press("Escape");
            await dialog.waitFor({state: "hidden"});
        }
        const details = page.getByRole("button", {name: "Show details", exact: true});
        await activate(details.nth(0));
        await dialog.getByText("Hidden item", {exact: true}).waitFor();
        await page.keyboard.press("Escape");
        await dialog.waitFor({state: "hidden"});
        await activate(details.nth(1));
        await dialog.getByText("Test plugin", {exact: true}).waitFor();
        await page.keyboard.press("Escape");
        await dialog.waitFor({state: "hidden"});
        assert.equal(await page.evaluate(() => window.submits), 0);
        assert.deepEqual(errors, []);
        await context.close();
    }
    console.log("Popup mouse, touch, keyboard, dismissal, Markdown, overflow chips, plugin icons, and form checks passed.");
} finally {
    await browser?.close();
    await server?.close();
    await rm(fixture, {recursive: true, force: true});
}
