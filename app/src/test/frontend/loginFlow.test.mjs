import assert from "node:assert/strict";
import {readFileSync} from "node:fs";
import {test} from "node:test";
import {runInNewContext} from "node:vm";
import ts from "typescript";

function fixture({user, error = false, from} = {}) {
    const window = {location: {href: "/login", pathname: "/login", search: "", hash: ""}};
    const effects = [];
    const elements = [];
    const fields = [];
    const authState = {user, initializing: false, loading: false};
    const noop = () => {};
    const jsx = (type, props) => { const element = {type, props}; elements.push(element); return element; };
    const dependencies = {
        "react": {useEffect: effect => effects.push(effect), useState: initial => [initial, noop],
            useRef: initial => ({current: initial})},
        "react/jsx-runtime": {jsx, jsxs: jsx},
        "react-router": {useLocation: () => ({state: {from}})},
        "Frontend/util/auth": {useAuth: () => ({
            state: authState,
            login: async (username, password, options) => {
                assert.equal(username, "alice");
                assert.equal(password, "secret");
                options.navigate("/stale-saved-request");
                assert.equal(window.location.href, "/login");
                return {error, redirectUrl: "/stale-saved-request"};
            }
        })},
        "@heroui/react": {Card: {Header: noop, Content: noop}, useOverlayState: () => ({})},
        "formik": {Formik: "Formik"},
        "Frontend/generated/endpoints": {RegistrationEndpoint: {isSelfRegistrationAllowed: async () => false}},
        "Frontend/components/general/input/Input": {},
        "Frontend/components/general/modals/PasswordResetModal": {},
        "Frontend/components/general/modals/SignUpModal": {}
    };
    function load(path) {
        const source = readFileSync(new URL(`../../main/frontend/${path}`, import.meta.url), "utf8");
        const compiled = ts.transpileModule(source, {
            compilerOptions: {module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX}
        }).outputText;
        const exports = {};
        runInNewContext(`(function(require, exports) {${compiled}\n})`, {window})(specifier => {
            assert.ok(specifier in dependencies, `Unexpected dependency: ${specifier}`);
            return dependencies[specifier];
        }, exports);
        return exports;
    }
    dependencies["Frontend/util/login"] = load("util/login.ts");
    return {
        window, effects, fields, authState,
        render: path => load(path).default(),
        submit: () => elements.find(e => e.type === "Formik").props.onSubmit(
            {username: "alice", password: "secret"}, {setFieldError: (...args) => fields.push(args)})
    };
}

test("successful local login consumes server session destination instead of Hilla's saved URL", async () => {
    const f = fixture();
    f.render("views/LoginView.tsx");
    await f.submit();
    assert.equal(f.window.location.href, "/login/success");
});

test("failed local login stays on form and displays credential errors", async () => {
    const f = fixture({error: true});
    f.render("views/LoginView.tsx");
    await f.submit();
    assert.equal(f.window.location.href, "/login");
    assert.equal(f.fields.length, 2);
});

test("authenticated local login view also consumes server session destination", () => {
    const f = fixture({user: {id: 1}});
    f.render("views/LoginView.tsx");
    f.effects.forEach(effect => effect());
    assert.equal(f.window.location.href, "/login/success");
});

test("form success and auth effect trigger only one consuming navigation", async () => {
    const f = fixture();
    const navigations = [];
    let href = "/login";
    Object.defineProperty(f.window.location, "href", {
        get: () => href, set: value => { href = value; navigations.push(value); }
    });
    f.render("views/LoginView.tsx");
    await f.submit();
    f.authState.user = {id: 1};
    f.effects.forEach(effect => effect());
    assert.deepEqual(navigations, ["/login/success"]);
});

test("protected client routes reach server gateway with original location", () => {
    const f = fixture({from: {pathname: "/settings/profile", search: "?tab=account", hash: "#name"}});
    f.render("views/LoginRedirectView.tsx");
    f.effects.forEach(effect => effect());
    assert.equal(f.window.location.href, "/loginredirect?continue=" +
        encodeURIComponent("/settings/profile?tab=account#name"));
});

test("missing roles do not send authenticated users into another login loop", () => {
    const f = fixture({user: {id: 1}});
    f.render("views/LoginRedirectView.tsx");
    f.effects.forEach(effect => effect());
    assert.equal(f.window.location.href, "/");
});
