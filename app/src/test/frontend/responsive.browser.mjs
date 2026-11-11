import assert from "node:assert/strict";
import {mkdir, readFile} from "node:fs/promises";
import {resolve} from "node:path";
const {chromium} = await import(process.env.PLAYWRIGHT_MODULE || "playwright");
const baseURL = process.env.RESPONSIVE_BASE_URL || "http://127.0.0.1:5173";
const artifacts = resolve(process.env.RESPONSIVE_ARTIFACTS || "build/responsive-review");
await mkdir(artifacts, {recursive: true});
const browser = await chromium.launch({headless:true,args:['--disable-features=LocalNetworkAccessChecks']});
let role='anonymous';
const page = await browser.newPage({viewport:{width:1280,height:800}});
page.on('response', r=>{if(r.status()>=400)console.log(r.status(),r.url())});
const pageErrors = [];
page.on('pageerror', error=>pageErrors.push(error.message));
// Standalone Vite has no Hilla-generated HTML bootstrap. Supply that shell only;
// all React components, styles, routing and state stores remain the application's.
await page.route(baseURL + "/**", route => {
    if (route.request().resourceType() !== "document") return route.fallback();
    return route.fulfill({contentType: "text/html", body: `<html><head><base href="/"><meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover, interactive-widget=resizes-content"></head><body><div id="outlet"></div><script type="module">import RefreshRuntime from "/@react-refresh"; RefreshRuntime.injectIntoGlobalHook(window); window.$RefreshReg$ = () => {}; window.$RefreshSig$ = () => type => type; window.__vite_plugin_react_preamble_installed__ = true;</script><script type="module" src="/index.tsx"></script></body></html>`});
});
// Static servlet resources are not exposed by standalone Vite.
await page.route("**/images/Logo.svg", async route => route.fulfill({
    contentType: "image/svg+xml", body: await readFile(new URL("../../main/resources/META-INF/resources/images/Logo.svg", import.meta.url))
}));
const calls = [];
page.on('console', msg=>{if(msg.type()==='error')console.log(msg.text().slice(0,500))});
await page.route('**/connect/**', async route => {
 const url = new URL(route.request().url());
 const name = url.pathname.split('/').slice(-2).join('/');
 calls.push(name);
 const game = {id:1,libraryId:1,title:'A very long game title for responsive layout testing',createdAt:'2026-01-01',updatedAt:'2026-01-01',platforms:['PC'],genres:[],themes:[],features:[],keywords:[],perspectives:[],developers:[],publishers:[],path:'/games/long/path/game.zip',matchConfirmed:false,downloadCount:7,release:'2025-01-01',metadata:{path:'/games/long/path/game.zip',fileSize:100,matchConfirmed:false},images:[],videoUrls:[]};
 const library = {id:1,name:'Library with a long name',createdAt:'2026-01-01',platforms:['PC'],metadata:{displayOnHomepage:true,displayOrder:0},directories:[],ignoredPaths:[{id:1,path:"/games/a-very-long-directory-name/game.zip",sourceType:"USER",source:"1"},{id:2,path:"/games/plugin-ignored.zip",sourceType:"PLUGIN",source:"[]"}]};
 const run = {id:'run-1',taskKey:'scan',taskName:'Scan libraries',displayName:'Scan libraries with a very long display name',trigger:'MANUAL',status:'IN_PROGRESS',startedAt:'2026-01-01T00:00:00Z',cancellable:true,cancelRequested:false,visibleInOverview:true};
 const task = {key:'scan',name:'Scan libraries',description:'Scan a long library path for games and metadata',source:'CORE',schedulable:true,manuallyTriggerable:true,cancellable:true,scheduleEnabled:false,settingsMetadata:[],settings:{},parametersMetadata:[],lastRun:run,running:true};
 let result=[];
 if(name==='UserEndpoint/getUserInfo')result=role==='anonymous'?null:{id:1,username:'Layout tester',email:'tester@example.test',enabled:true,roles:role==='admin'?['ROLE_ADMIN']:['ROLE_USER'],managedBySso:false};
 else if(name==='GameEndpoint/sync')result={serverTime:'2026-01-01',full:true,games:Array.from({length:2000},(_,i)=>({...game,id:i+1,title:game.title+' '+i})),allIds:Array.from({length:2000},(_,i)=>i+1)};
 else if(name==='LibraryEndpoint/sync')result={serverTime:'2026-01-01',full:true,libraries:[library],allIds:[1]};
 else if(name==='CollectionEndpoint/sync')result={serverTime:'2026-01-01',full:true,collections:[{id:1,name:'Collection with a long name',createdAt:'2026-01-01',updatedAt:'2026-01-01',gameIds:[1],metadata:{displayOnHomepage:false,displayOrder:0}}],allIds:[1]};
 else if(name==='GameEndpoint/getById')result={...game,id:route.request().postDataJSON().id};
 else if(name==='LibraryEndpoint/getById')result=library;
 else if(name==='SetupEndpoint/isSetupCompleted')result=true;
 else if(name==='UserPreferencesEndpoint/get')result=null;
 else if(name==='GameEndpoint/getEnumPropertyValues')result={genres:[],themes:[],features:[],perspectives:[]};
 else if(name==='SetupEndpoint/getDatabaseInfo')result={type:'H2',managedExternally:true};

 else if(name==='GameRequestEndpoint/getAll')result=[{id:1,title:'Requested game with a long title',platform:'PC',release:'2025-01-01',createdAt:'2026-01-01',updatedAt:'2026-01-01',status:'PENDING',voters:[],requester:{id:1,username:'Layout tester'}}];
 else if(name==='CredentialEndpoint/list')result=[{id:'credential-1',kind:'PERSONAL',name:'Credential with a long name',prefix:'gfy_123',scopes:['games:read'],createdAt:'2026-01-01',status:'ACTIVE'}];
 else if(name==='BackupEndpoint/getBackups')result=[{name:'backup-with-a-long-name.zip',sizeBytes:1024,createdAt:'2026-01-01',gameyfinVersion:'2.6.1',includesMedia:true,restorable:true}];
 else if(name==='BackupEndpoint/getLastRestoreStatus')result=null;
 else if(name==='DatabaseEndpoint/getDatabaseInfo')result={type:'H2',managedExternally:true,passwordSet:false,settingsFile:'/config/database'};
 else if(name==='GameEndpoint/getPotentialMatches')result=route.request().postDataJSON().searchTerm==='empty'?[]:[{id:'match-1',title:'Matched game with a long title',developers:['A developer with a long name'],publishers:['Publisher'],release:'2025-01-01',platforms:['PC'],originalIds:{fixture:{pluginId:'fixture',externalId:'1'}}}];
 else if(name==='ConfigEndpoint/areGameRequestsEnabled' || name==='ConfigEndpoint/areGuestsAllowedToRequestGames')result=true;
 else if(name==='CredentialEndpoint/scopes')result=['games:read','games:write'];
 else if(name==='TaskEndpoint/getTasks')result=[task];
 else if(name==='TaskEndpoint/getRuns')result={runs:[run],hasMore:false};
 else if(name==='TaskEndpoint/runNow')result=run;
 else if(name==='DownloadProviderEndpoint/getProviders')result=[{key:'http',name:'Download',description:'HTTP download'}];
 else if(name==='ConfigEndpoint/getAll')result=[
    ['logs.folder','Log folder','string','/logs'],['logs.max-history-days','Log history','int',7],
    ['logs.level.gameyfin','Gameyfin log level','string','INFO'],['logs.level.root','Root log level','string','WARN'],
    ['users.sign-ups.allow','Allow sign-ups','boolean',true],['users.sign-ups.confirmation-required','Require confirmation','boolean',true],
    ['requests.games.enabled','Enable requests','boolean',true],['requests.games.allow-guests-to-request-games','Allow guest requests','boolean',true],
    ['requests.games.max-open-requests-per-user','Maximum requests','int',5],['security.allow-public-access','Public access','boolean',true],
    ['downloads.bandwidth-limit.enabled','Limit bandwidth','boolean',false],['downloads.bandwidth-limit.mbps','Bandwidth','int',10]
 ].map(([key,name,type,value])=>({key,name,type,value,defaultValue:value,description:''}));
 
 await route.fulfill({json:result});
});


const viewports = [[393,852],[430,932],[820,1180],[834,1194],[852,393],[932,430],
    [1180,820],[1194,834],[639,900],[640,900],[1279,800],[1280,800],[1728,1117],[1920,1080]];
async function go(path) {
    await page.goto(baseURL + path);
    await page.waitForTimeout(600);
}
async function capture(name, sizes = viewports) {
    for (const [width, height] of sizes) {
        await page.setViewportSize({width, height});
        await page.waitForTimeout(300);
        const overflow = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth);
        assert.ok(overflow <= 1, `${name} overflows ${overflow}px at ${width}×${height}`);
        await page.screenshot({path: resolve(artifacts, `${name}-${width}x${height}.png`)});
    }
}
try {
    await go("/");
    await page.getByText("All games", {exact: true}).first().waitFor();
    await capture("home");
    await page.setViewportSize({width:393, height:852});
    await page.getByRole("button", {name:"More", exact:true}).click();
    await page.getByRole("dialog").waitFor();
    assert.equal(await page.getByRole("dialog").getByRole("button", {name:"Administration", exact:true}).count(), 0);
    await page.keyboard.press("Escape");
    await page.getByRole("dialog").waitFor({state:"hidden"});
    assert.equal(await page.evaluate(() => document.activeElement?.textContent), "More");
    await page.getByPlaceholder("Type to search...").fill("Preserved search");
    await page.setViewportSize({width:1280, height:800});
    assert.equal(await page.getByPlaceholder("Type to search...").inputValue(), "Preserved search");

    for (const path of ["/search", "/game/1", "/requests", "/login"]) {
        await go(path);
        await capture(path.slice(1).replaceAll("/", "-"), [[393,852],[834,1194],[1280,800]]);
    }
    role = "user";
    await go("/");
    await page.setViewportSize({width:393, height:852});
    await page.getByRole("button", {name:"More", exact:true}).click();
    await page.getByRole("dialog").waitFor();
    assert.equal(await page.getByRole("dialog").getByRole("button", {name:"My Profile", exact:true}).count(), 1);
    assert.equal(await page.getByRole("dialog").getByRole("button", {name:"Administration", exact:true}).count(), 0);

    role = "admin";
    await go("/administration/games/library/1#games");
    await page.getByRole("heading", {name:"Manage games in library"}).waitFor();
    await capture("library-management");
    await page.setViewportSize({width:393, height:852});
    // Two thousand records must not create two thousand interactive rows.
    assert.ok(await page.getByRole("button", {name:"Edit metadata", exact:true}).count() < 30);
    await page.getByRole("button", {name:"Edit metadata", exact:true}).first().click();
    await page.getByRole("dialog").waitFor();
    await page.locator("input[name=title]").fill("Unsaved phone edit");
    await capture("editor");
    assert.equal(await page.locator("input[name=title]").inputValue(), "Unsaved phone edit");
    await page.setViewportSize({width:393, height:450});
    const save = page.getByRole("dialog").getByRole("button", {name:"Save", exact:true});
    await save.scrollIntoViewIfNeeded();
    const box = await save.boundingBox();
    assert.ok(box && box.y >= 0 && box.y + box.height <= 450, "Save stays reachable in a reduced viewport");
    await page.getByRole("dialog").locator(".modal__close-trigger").click();
    await page.getByRole("dialog").waitFor({state:"hidden"});
    assert.equal(calls.includes("GameEndpoint/updateGame"), false, "Resizing and dismissing must not save drafts");
    await page.getByRole("button", {name:/Details for/}).first().click();
    await page.getByRole("dialog").waitFor();
    await page.setViewportSize({width:1280, height:800});
    assert.equal(await page.getByRole("dialog").isVisible(), true);
    await page.keyboard.press("Escape");
    await page.getByRole("dialog").waitFor({state:"hidden"});

    await go("/administration/games/collection/1");
    await page.getByRole("heading", {name:"Manage games in collection"}).waitFor();
    await capture("collection-management", [[393,852],[834,1194],[1280,800]]);
    await page.setViewportSize({width:393, height:852});
    await page.locator('input[name="name"]').fill("Unsaved collection");
    await page.getByRole("button", {name:"Remove game from collection", exact:true}).first().click();
    assert.ok(calls.includes("CollectionEndpoint/removeGameFromCollection"));
    await page.setViewportSize({width:1280, height:800});
    assert.equal(await page.locator('input[name="name"]').inputValue(), "Unsaved collection");

    for (const path of ["/settings/appearance", "/setup"]) {
        await go(path);
        await capture(path.slice(1).replaceAll("/", "-"), [[393,852],[834,1194],[1280,800],[1728,1117],[1920,1080]]);
    }
    // Every former compact table scroller now has a card presentation.
    await go("/administration/api-keys");
    await page.setViewportSize({width:393,height:852});
    await page.getByText("Read", {exact:true}).click();
    await page.locator('input[name="name"]').fill("Permission draft");
    await capture("permission-cards", [[393,852],[834,1194],[1279,800],[1280,800]]);
    assert.equal(await page.getByRole("checkbox", {name:"Games: read",exact:true}).isChecked(), true);
    assert.equal(await page.locator('input[name="name"]').inputValue(), "Permission draft");
    await page.setViewportSize({width:393,height:852});
    await page.waitForFunction(() => document.querySelectorAll("main table").length === 0);
    assert.equal(await page.locator("main table").count(), 0);
    await page.waitForFunction(() => ![...document.querySelectorAll('.table-region-hint')].some(el => el.getBoundingClientRect().height > 0));
    assert.equal(await page.locator('.table-region-hint:visible').count(), 0);

    await go("/administration/system");
    await capture("backup-cards", [[393,852],[834,1194],[1280,800]]);
    await page.setViewportSize({width:393,height:852});
    await page.waitForFunction(() => ![...document.querySelectorAll('.table-region-hint')].some(el => el.getBoundingClientRect().height > 0));
    assert.equal(await page.locator('.table-region-hint:visible').count(), 0);
    await page.getByRole("button", {name:"Delete backup",exact:true}).click();
    await page.getByRole("dialog").waitFor();
    await page.setViewportSize({width:1280,height:800});
    assert.equal(await page.getByRole("dialog").isVisible(), true);
    await page.keyboard.press("Escape");
    assert.equal(calls.includes("BackupEndpoint/deleteBackup"), false);

    await go("/administration/games/library/1#ignored-paths");
    await page.getByRole("heading", {name:"Manage ignored paths"}).waitFor();
    await capture("ignored-path-cards", [[393,852],[834,1194],[1279,800],[1280,800]]);
    await page.setViewportSize({width:393,height:852});
    await page.waitForFunction(() => ![...document.querySelectorAll('.table-region-hint')].some(el => el.getBoundingClientRect().height > 0));
    assert.equal(await page.locator('.table-region-hint:visible').count(), 0);
    assert.equal(await page.getByRole("button", {name:"Remove entry from list",exact:true}).nth(1).isDisabled(), true);
    await page.getByRole("button", {name:"Match game",exact:true}).first().click();
    let dialog = page.getByRole("dialog");
    await dialog.getByRole("textbox", {name:"Search games",exact:true}).fill("A match query");
    await dialog.getByRole("button", {name:"Search games",exact:true}).click();
    await dialog.getByRole("button", {name:"Pick Matched game with a long title",exact:true}).waitFor();
    await capture("match-result-cards", [[393,852],[834,1194],[1280,800]]);
    assert.equal(await dialog.getByRole("textbox", {name:"Search games",exact:true}).inputValue(), "A match query");
    await page.setViewportSize({width:393,height:852});
    await dialog.locator('table').waitFor({state: 'detached'});
    assert.equal(await dialog.locator('table').count(), 0);
    await dialog.getByRole("button", {name:"Pick Matched game with a long title",exact:true}).click();
    await dialog.waitFor({state:"hidden"});
    assert.ok(calls.includes("GameEndpoint/matchManually"));

    await go("/requests");
    await page.getByRole("button", {name:"Request a Game",exact:true}).click();
    dialog = page.getByRole("dialog");
    await dialog.getByRole("textbox", {name:"Search games",exact:true}).fill("A request query");
    await dialog.getByRole("button", {name:"Search games",exact:true}).click();
    await dialog.getByRole("button", {name:"Pick Matched game with a long title",exact:true}).waitFor();
    await capture("request-result-cards", [[393,852],[834,1194],[1280,800]]);
    await page.setViewportSize({width:393,height:852});
    await dialog.locator('table').waitFor({state: 'detached'});
    assert.equal(await dialog.locator('table').count(), 0);
    await dialog.getByRole("button", {name:"Pick Matched game with a long title",exact:true}).click();
    await dialog.waitFor({state:"hidden"});
    assert.ok(calls.includes("GameRequestEndpoint/create"));

    // Compact records retain all actions and state while the desktop table is unmounted.
    for (const path of ["/requests", "/administration/api-keys", "/settings/tokens", "/administration/tasks"]) {
        await go(path);
        await capture("cards-" + path.replaceAll("/", "-"), [[393,852],[430,932],[834,1194],[1279,800],[1280,800]]);
        await page.setViewportSize({width:393,height:852});
        await page.waitForFunction(() => document.querySelectorAll("main table").length === 0);
    assert.equal(await page.locator("main table").count(), 0);
        if (path.includes("tokens") || path.includes("api-keys")) {
            await page.getByRole("button", {name:"Edit",exact:true}).click();
            await page.locator('input[name="name"]').fill("Unsaved credential draft");
            await page.getByRole("button", {name:"Revoke",exact:true}).click();
            await page.setViewportSize({width:1280,height:800});
            assert.equal(await page.locator('input[name="name"]').inputValue(), "Unsaved credential draft");
            assert.equal(await page.getByRole("button", {name:"Confirm revoke",exact:true}).count(), 1);
        } else if (path === "/requests") {
            await page.getByRole("button", {name:"Vote for this request",exact:true}).click();
            assert.ok(calls.includes("GameRequestEndpoint/toggleVote"));
            assert.equal(await page.getByRole("button", {name:"Approve this request",exact:true}).count(), 1);
            await page.getByRole("button", {name:"Descending",exact:true}).click();
            await page.setViewportSize({width:1280,height:800});
            assert.equal(await page.locator('th[aria-sort="ascending"]').count(), 1);
        } else {
            await page.getByRole("button", {name:"Run now",exact:true}).click();
            assert.ok(calls.includes("TaskEndpoint/runNow"));
            await page.getByRole("button", {name:"Show log",exact:true}).click();
            await page.getByRole("dialog").waitFor();
            await page.setViewportSize({width:1280,height:800});
            assert.equal(await page.getByRole("dialog").isVisible(), true);
            await page.keyboard.press("Escape");
        }
    }
    for (const path of ["/administration/logs", "/administration/users", "/administration/requests", "/administration/downloads"]) {
        await go(path);
        await capture("stacked-" + path.replaceAll("/", "-"), [[393,852],[834,1194],[1280,800]]);
    }
    await go("/game/1");
    await page.setViewportSize({width:393,height:852});
    const downloadWidth = await page.getByRole("button", {name:/Download/}).first().evaluate(el => ({width:el.getBoundingClientRect().width,parent:el.parentElement.parentElement.getBoundingClientRect().width}));
    assert.ok(Math.abs(downloadWidth.width - downloadWidth.parent) < 2, "Phone download fills its action row");
    await go("/settings/appearance");
    await page.setViewportSize({width:393, height:852});
    const sectionMenu = page.getByRole("button", {name:"Open section menu"});
    const beforeScroll = await sectionMenu.boundingBox();
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
    const afterScroll = await sectionMenu.boundingBox();
    assert.equal(beforeScroll.y, afterScroll.y, "Section menu stays fixed while the header scrolls");
    assert.ok(afterScroll.x > 393 / 2);
    await page.getByRole("button", {name:"Open section menu"}).click();
    await page.getByRole("dialog").waitFor();
    await page.setViewportSize({width:1280, height:800});
    await page.getByRole("dialog").waitFor({state:"hidden"});
    await page.emulateMedia({colorScheme:"light", reducedMotion:"reduce"});
    await page.evaluate(() => { localStorage.setItem("theme", "light"); });
    await go("/search");
    await capture("search-light-reduced-motion", [[393,852],[1280,800]]);
    assert.deepEqual(pageErrors, []);
    console.log(`Responsive browser checks passed. Screenshots: ${artifacts}`);
} finally {
    await browser.close();
}
