import {AuthContext, configureAuth} from '@vaadin/hilla-react-auth';
import {UserEndpoint} from 'Frontend/generated/endpoints';
import {createElement, PropsWithChildren, useEffect, useState} from "react";
import ExtendedUserInfoDto from "Frontend/generated/org/gameyfin/app/users/dto/ExtendedUserInfoDto";
import {liveSubscription} from "Frontend/state/sync/liveSubscription";

// Keep Hilla's login/logout integration and overlay the current-user websocket state.
const auth = configureAuth(UserEndpoint.getUserInfo);

// Both application consumers and Hilla's protected routes use the same context.
export const useAuth = auth.useAuth;
export function AuthProvider({children}: PropsWithChildren) {
    return createElement(auth.AuthProvider, null, createElement(LiveAuthProvider, null, children));
}

function LiveAuthProvider({children}: PropsWithChildren) {
    const base = auth.useAuth();
    const [live, setLive] = useState<{identity: number; user?: ExtendedUserInfoDto}>();
    const identity = base.state.user?.id;
    const user = live?.identity === identity ? live?.user : base.state.user;

    useEffect(() => {
        if (identity === undefined) { setLive(undefined); return; }
        return liveSubscription({
            subscribe: () => UserEndpoint.subscribeCurrentUser(),
            fetch: () => UserEndpoint.getUserInfo(),
            replace: value => {
                setLive({identity, user: value?.enabled ? value : undefined});
                if (!value?.enabled) void base.logout();
            },
            apply: events => events.forEach(event => {
                if (event.id === identity) {
                    setLive({identity, user: event.user?.enabled ? event.user : undefined});
                    if (!event.user?.enabled) void base.logout();
                }
            })
        });
    }, [identity]);

    const value: ReturnType<typeof useAuth> = {
        ...base,
        state: {...base.state, user},
        hasAccess: access => {
            const required = access.loginRequired ?? access.requiresLogin ?? access.rolesAllowed;
            if (!required) return true;
            if (!user) return false;
            const roles = new Set<string>(user.roles);
            if (roles.has("ROLE_SUPERADMIN")) roles.add("ROLE_ADMIN");
            if (roles.has("ROLE_ADMIN")) roles.add("ROLE_USER");
            return !access.rolesAllowed || access.rolesAllowed.some(role => roles.has(role));
        }
    };
    return createElement(AuthContext.Provider, {value}, children);
}


export function getCsrfToken() {
    const token = document.querySelector('meta[name="_csrf"]')?.getAttribute('content');
    return token || '';
}
