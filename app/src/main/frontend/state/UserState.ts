import {proxy} from "valtio";
import {UserEndpoint} from "Frontend/generated/endpoints";
import ExtendedUserInfoDto from "Frontend/generated/org/gameyfin/app/users/dto/ExtendedUserInfoDto";
import {liveSubscription} from "Frontend/state/sync/liveSubscription";

type UserState = {
    isLoaded: boolean;
    state: Record<number, ExtendedUserInfoDto>;
    users: ExtendedUserInfoDto[];
};

export const userState = proxy<UserState>({
    isLoaded: false,
    state: {},
    get users() {
        return Object.values<ExtendedUserInfoDto>(this.state);
    }
});

let identity: string | undefined;
let stop: (() => void) | undefined;

export function resetUserState() {
    stop?.();
    stop = undefined;
    identity = undefined;
    userState.state = {};
    userState.isLoaded = false;
}

export function initializeUserState(newIdentity: string) {
    if (identity === newIdentity) return;
    resetUserState();
    identity = newIdentity;
    stop = liveSubscription({
        subscribe: () => UserEndpoint.subscribe(),
        fetch: () => UserEndpoint.getAllUsers(),
        replace: users => {
            userState.state = Object.fromEntries(users.map(user => [user.id, user]));
            userState.isLoaded = true;
        },
        apply: events => events.forEach(event => {
            if (event.user) userState.state[event.id] = event.user;
            else delete userState.state[event.id];
        })
    });
}
