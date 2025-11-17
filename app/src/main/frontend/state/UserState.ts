import {proxy} from "valtio";
import {UserEndpoint} from "Frontend/generated/endpoints";
import ExtendedUserInfoDto from "Frontend/generated/org/gameyfin/app/users/dto/ExtendedUserInfoDto";

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

/** Fetch and cache all users **/
export async function initializeUserState() {
    if (userState.isLoaded) return;

    try {
        const allUsers = await UserEndpoint.getAllUsers();
        allUsers.forEach((user: ExtendedUserInfoDto) => {
            userState.state[user.id] = user;
        });
        userState.isLoaded = true;
    } catch (error) {
        console.error("Failed to load users:", error);
    }
}