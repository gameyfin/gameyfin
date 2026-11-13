import assert from "node:assert/strict";
import {mkdtemp, rm, writeFile} from "node:fs/promises";
import {fileURLToPath} from "node:url";
import {resolve} from "node:path";
import {createServer} from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

const {chromium} = await import(process.env.PLAYWRIGHT_MODULE || "playwright");
const app = fileURLToPath(new URL("../../../", import.meta.url));
const fixture = await mkdtemp(resolve(app, "notification-test-"));
let server;
let browser;
try {
    await writeFile(resolve(fixture, "index.html"), '<meta name="viewport" content="width=device-width, initial-scale=1"><div id="root"></div><script type="module" src="./fixture.tsx"></script>');
    await writeFile(resolve(fixture, "empty.tsx"), 'export default function Empty() {return null;}');
    await writeFile(resolve(fixture, "config.ts"), 'export default function withConfigPage(Component) {return Component;}');
    await writeFile(resolve(fixture, "restart.ts"), 'export const waitForRestart = () => new Promise(() => {});');
    await writeFile(resolve(fixture, "endpoints.ts"), `
        let next;
        const base = {createdAt: new Date().toISOString(), source:"core:test", severity:"INFO", readAt:undefined, executionState:"READY", shared:true};
        let rows = [
            {...base, id:"welcome", title:"Welcome", body:"**Markdown** [Link](https://example.com)\\n<script>window.xss=true</script>", actions:[]},
            {...base, id:"restart", severity:"WARNING", title:"Restart required", body:"Restart to apply SSO changes.", actions:[{key:"restart",label:"Restart Gameyfin",type:"RESTART",available:true}]}
        ];
        window.calls = {action:0, system:0, read:0, deleted:0};
        const changed = () => next?.([{changed:true}]);
        export const NotificationEndpoint = {
            subscribe: () => ({onNext(fn) {next=fn;return this}, onConnectionStateChange(){return this}, onSubscriptionLost(){return this}, onError(){return this}, cancel(){}}),
            getInbox: async (archived, page, size, severity) => {
                if (window.holdInbox) await new Promise(resolve => {window.releaseInbox = resolve;});
                const items = rows.filter(row => Boolean(row.readAt) === archived && (!severity || row.severity === severity));
                return {items:items.slice(page*size,(page+1)*size),total:items.length,unreadCount:rows.filter(row=>!row.readAt).length};
            },
            markRead: async ids => {window.calls.read++; rows = rows.map(row=>ids.includes(row.id)?{...row,readAt:new Date().toISOString()}:row); changed();},
            markAllRead: async () => {rows=rows.map(row=>({...row,readAt:new Date().toISOString()}));changed();},
            deleteArchived: async ids => {window.calls.deleted++;rows=rows.filter(row=>!ids.includes(row.id));changed();},
            deleteAllArchived: async () => {rows=rows.filter(row=>!row.readAt);changed();},
            executeAction: async () => {window.calls.action++;}
        };
        export const SystemEndpoint = {restart:async()=>{window.calls.system++}};
        window.pushNotification = () => {rows.push({...base,id:"push",title:"Live message",body:"Pushed while connected",actions:[]});changed();};
    `);
    await writeFile(resolve(fixture, "fixture.tsx"), `
        import React from "react";
        import {createRoot} from "react-dom/client";
        import {Toast} from "@heroui/react";
        import NotificationPopover from "Frontend/components/general/NotificationPopover";
        import {SystemManagement} from "Frontend/components/administration/SystemManagement";
        import {initializeNotificationState} from "Frontend/state/NotificationState";
        import "Frontend/main.css";
        initializeNotificationState("admin");
        createRoot(document.getElementById("root")).render(<div style={{padding:24}}>
            <NotificationPopover/><SystemManagement/><Toast.Provider/>
        </div>);
    `);
    server = await createServer({
        configFile: false, root: app, cacheDir: resolve(fixture, ".vite"), plugins: [react(), tailwindcss()],
        resolve: {
            alias: {
                "Frontend/generated/endpoints": resolve(fixture, "endpoints.ts"),
                "Frontend/util/restart": resolve(fixture, "restart.ts"),
                "Frontend/components/administration/withConfigPage": resolve(fixture, "config.ts"),
                "Frontend/components/administration/BackupManagement": resolve(fixture, "empty.tsx"),
                "Frontend/components/administration/DatabaseManagement": resolve(fixture, "empty.tsx"),
                Frontend: resolve(app, "src/main/frontend")
            }
        }, server: {host: "127.0.0.1", port: 0}
    });
    await server.listen();
    browser = await chromium.launch({headless: true});
    for (const width of [1440, 393]) {
        const context = await browser.newContext({viewport: {width, height: 852}, hasTouch: width === 393});
        const page = await context.newPage();
        const errors = [];
        page.on("pageerror", error => errors.push(error.message));
        await page.goto(`${server.resolvedUrls.local[0]}${fixture.split("/").pop()}/index.html`);
        const bell = page.getByRole("button", {name: "Notifications, 2 unread", exact: true});
        await bell.waitFor();
        await bell.focus();
        await page.keyboard.press("Enter");
        const dialog = page.getByRole("dialog", {name: "Notifications", exact: true});
        await dialog.waitFor();
        assert.equal(await dialog.locator("strong").textContent(), "Markdown");
        assert.equal(await page.evaluate(() => Boolean(window.xss)), false);
        assert.equal(await page.evaluate(() => window.calls.read), 0);
        const bounds = await dialog.boundingBox();
        assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= width + 1);
        const controls = dialog.getByRole("group", {name: "Notification controls", exact: true});
        const buttons = await controls.getByRole("button").all();
        const positions = await Promise.all(buttons.map(button => button.boundingBox()));
        assert.ok(positions.every(position => Math.abs(position.y - positions[0].y) < 1), "Controls stay on one row");
        assert.equal(await controls.getByRole("tab", {
            name: "Unread",
            exact: true
        }).getAttribute("aria-selected"), "true");
        await dialog.getByRole("button", {name: "Filter severity: all", exact: true}).click();
        await page.getByRole("menuitemradio", {name: "Warning", exact: true}).click();
        await dialog.getByRole("heading", {name: "Restart required", exact: true}).waitFor();
        assert.equal(await dialog.getByRole("heading", {name: "Welcome", exact: true}).count(), 0);
        const row = dialog.locator("article").first();
        assert.equal(await row.getByRole("img", {name: "Shared admin notification"}).count(), 1);
        const actionBounds = await row.getByRole("button", {name: "Restart Gameyfin", exact: true}).boundingBox();
        const readBounds = await row.getByRole("button", {name: "Mark as read", exact: true}).boundingBox();
        assert.ok(readBounds.y > actionBounds.y && Math.abs(readBounds.x - actionBounds.x) < 1);
        await dialog.getByRole("button", {name: "Filter severity: warning", exact: true}).click();
        await page.getByRole("menuitemradio", {name: "All severities", exact: true}).click();
        await dialog.getByRole("heading", {name: "Welcome", exact: true}).waitFor();
        const originalHeight = (await dialog.getByRole("tabpanel").boundingBox()).height;
        await page.evaluate(() => {
            window.holdInbox = true;
        });
        await dialog.getByRole("tab", {name: "Archive", exact: true}).click();
        assert.equal(await dialog.getByRole("status").count(), 0, "Skeleton is delayed");
        assert.ok((await dialog.getByRole("tabpanel").boundingBox()).height >= originalHeight - 1,
            "Previous panel height is retained during loading");
        await dialog.getByRole("status").waitFor();
        await page.evaluate(() => {
            window.holdInbox = false;
            window.releaseInbox();
        });
        await dialog.getByText("No archived notifications.", {exact: true}).waitFor();
        assert.equal(await dialog.getByRole("status").count(), 0);
        await dialog.getByRole("tab", {name: "Unread", exact: true}).click();
        await dialog.getByRole("heading", {name: "Welcome", exact: true}).waitFor();
        const selectMode = dialog.getByRole("button", {name: "Select notifications", exact: true});
        assert.equal(await dialog.getByRole("checkbox").count(), 0);
        await selectMode.click();
        await dialog.getByRole("checkbox", {name: "Select Welcome", exact: true}).check();
        await selectMode.click();
        assert.equal(await dialog.getByRole("checkbox").count(), 0);
        assert.equal(await dialog.getByRole("button", {name: "Mark selected as read", exact: true}).isDisabled(), true);
        await selectMode.focus();
        await page.keyboard.press("Space");
        assert.equal(await dialog.getByRole("checkbox", {name: "Select Welcome", exact: true}).isChecked(), false);
        await dialog.getByRole("checkbox", {name: "Select Welcome", exact: true}).check();
        await dialog.getByRole("button", {name: "Mark selected as read", exact: true}).click();
        await page.getByRole("button", {name: "Notifications, 1 unread", exact: true}).waitFor();
        await dialog.getByRole("tab", {name: "Archive", exact: true}).click();
        await dialog.getByRole("heading", {name: "Welcome", exact: true}).waitFor();
        await dialog.getByRole("button", {name: "Delete", exact: true}).click();
        await dialog.getByText("No archived notifications.", {exact: true}).waitFor();
        await dialog.getByRole("tab", {name: "Unread", exact: true}).click();
        await page.evaluate(() => window.pushNotification());
        await dialog.getByRole("heading", {name: "Live message", exact: true}).waitFor();
        await dialog.getByRole("button", {name: "Restart Gameyfin", exact: true}).click();
        const confirmation = page.getByRole("dialog", {name: "Restart Gameyfin?", exact: true});
        await confirmation.waitFor();
        await confirmation.getByRole("button", {name: "Cancel", exact: true}).click();
        await confirmation.waitFor({state: "hidden"});
        assert.equal(await page.evaluate(() => window.calls.action), 0);
        // Reopen the popover if modal focus handling dismissed it.
        if (!(await dialog.isVisible())) await page.getByRole("button", {name: /^Notifications,/}).click();
        await dialog.getByRole("button", {name: "Restart Gameyfin", exact: true}).click();
        await confirmation.getByRole("button", {name: "Restart", exact: true}).click();
        await page.waitForFunction(() => window.calls.action === 1);
        await confirmation.waitFor({state: "hidden"});
        if (await dialog.isVisible()) await page.keyboard.press("Escape");
        await page.getByRole("button", {name: "Restart", exact: true}).click();
        await confirmation.waitFor();
        await confirmation.getByRole("button", {name: "Cancel", exact: true}).click();
        await confirmation.waitFor({state: "hidden"});
        assert.equal(await page.evaluate(() => window.calls.system), 0);
        await page.getByRole("button", {name: "Restart", exact: true}).click();
        await confirmation.getByRole("button", {name: "Restart", exact: true}).click();
        await page.waitForFunction(() => window.calls.system === 1);
        assert.deepEqual(errors, []);
        await context.close();
    }
    console.log("Notification desktop/mobile, keyboard, Markdown, read/archive/delete, live invalidation, and both restart confirmations passed.");
} finally {
    await browser?.close();
    await server?.close();
    await rm(fixture, {recursive: true, force: true});
}
