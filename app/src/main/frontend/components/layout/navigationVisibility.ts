/** Presentation visibility only; route and backend authorization remain authoritative. */
export function navigationVisibility(signedIn: boolean, administrator: boolean) {
    return {
        profile: signedIn,
        administration: signedIn && administrator,
        signIn: !signedIn,
        signOut: signedIn
    };
}
