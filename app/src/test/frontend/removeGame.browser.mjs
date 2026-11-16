import assert from "node:assert/strict";
import {mkdtemp, rm, writeFile} from "node:fs/promises";
import {fileURLToPath} from "node:url";
import {resolve} from "node:path";
import {createServer} from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

const {chromium} = await import(process.env.PLAYWRIGHT_MODULE || "playwright");
const app = fileURLToPath(new URL("../../../", import.meta.url));
const fixture = await mkdtemp(resolve(app, "build/remove-game-ui-"));
let server;
let browser;
try {
    await writeFile(resolve(fixture, "index.html"), '<div id="root"></div><script type="module" src="./fixture.tsx"></script>');
    await writeFile(resolve(fixture, "endpoints.ts"), `export const GameEndpoint = {deleteGame: async (id, deleteFiles) => {
        window.calls.push({id, deleteFiles});
        if (window.hold) await new Promise(resolve => window.finish = resolve);
        if (window.fail) throw new Error("Denied");
    }};`);
    await writeFile(resolve(fixture, "fixture.tsx"), `
        import React, {useState} from "react";
        import {createRoot} from "react-dom/client";
        import {Button} from "@heroui/react";
        import RemoveGameModal from "Frontend/components/general/modals/RemoveGameModal";
        import "Frontend/main.css";
        window.calls = [];
        function Fixture() {
            const [open, setOpen] = useState(false);
            return <><Button onPress={() => setOpen(true)}>Open removal</Button>
                <RemoveGameModal game={{id: 7, title: "Test game", path: "/games/test.zip"}}
                    isOpen={open} onOpenChange={setOpen}/></>;
        }
        createRoot(document.getElementById("root")).render(<Fixture/>);
    `);
    server = await createServer({configFile: false, root: app, cacheDir: resolve(fixture, ".vite"),
        plugins: [react(), tailwindcss()], resolve: {alias: {
            "Frontend/generated/endpoints": resolve(fixture, "endpoints.ts"),
            Frontend: resolve(app, "src/main/frontend")
        }}, server: {host: "127.0.0.1", port: 0}});
    await server.listen();
    browser = await chromium.launch({headless: true,
        ...(process.env.CHROME_EXECUTABLE ? {executablePath: process.env.CHROME_EXECUTABLE} : {})});
    const page = await browser.newPage();
    const errors = [];
    page.on("pageerror", error => errors.push(error.message));
    await page.goto(`${server.resolvedUrls.local[0]}build/${fixture.split("/").pop()}/index.html`);
    const dialog = page.getByRole("dialog", {name: "Remove from library"});
    const checkbox = page.getByRole("checkbox", {name: "Delete game files from disk"});
    const open = () => page.getByRole("button", {name: "Open removal"}).click();
    await open();
    assert.equal(await checkbox.isChecked(), false);
    await dialog.getByRole("button", {name: "Cancel"}).click();
    assert.deepEqual(await page.evaluate(() => window.calls), []);
    await open();
    await dialog.getByRole("button", {name: "Remove", exact: true}).click();
    await dialog.waitFor({state: "hidden"});
    assert.deepEqual(await page.evaluate(() => window.calls), [{id: 7, deleteFiles: false}]);
    await open();
    await dialog.getByText("Delete game files from disk", {exact: true}).click();
    await dialog.getByRole("alert").waitFor();
    await dialog.getByText("/games/test.zip", {exact: true}).waitFor();
    await dialog.getByRole("button", {name: "Cancel"}).click();
    await open();
    assert.equal(await checkbox.isChecked(), false, "Each removal must default to keeping files");
    await dialog.getByText("Delete game files from disk", {exact: true}).click();
    await page.evaluate(() => {window.hold = true; window.fail = true;});
    await dialog.getByRole("button", {name: "Remove and delete files"}).click();
    await page.waitForFunction(() => Boolean(window.finish));
    assert.equal(await dialog.getByRole("button", {name: "Cancel"}).isDisabled(), true);
    assert.equal(await checkbox.isDisabled(), true);
    await page.evaluate(() => window.finish());
    await page.waitForFunction(() => !document.querySelector('input[type="checkbox"]').disabled);
    assert.equal(await dialog.isVisible(), true, "Failure must keep the confirmation open");
    assert.deepEqual(await page.evaluate(() => window.calls), [{id: 7, deleteFiles: false}, {id: 7, deleteFiles: true}]);
    assert.deepEqual(errors, []);
    console.log("Game removal browser checks passed: default keep, cancel, reset, explicit delete, pending, failure.");
} finally {
    await browser?.close();
    await server?.close();
    await rm(fixture, {recursive: true, force: true});
}
