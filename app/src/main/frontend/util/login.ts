/** Use a full navigation so the server can select setup, local login, or SSO. */
export function redirectToLogin(returnPath = window.location.pathname + window.location.search + window.location.hash) {
    window.location.href = `/loginredirect?continue=${encodeURIComponent(returnPath)}`;
}
