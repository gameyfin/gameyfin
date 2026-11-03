import {useAuth} from "Frontend/util/auth";
import {Avatar as HeroUiAvatar} from "@heroui/react";
import {UserIcon} from "@phosphor-icons/react";
import {useSnapshot} from "valtio";
import {userState} from "Frontend/state/UserState";

interface AvatarProps {
    username?: string;
    className?: string;
    avatarId?: number;
    hasAvatar?: boolean;
}

const Avatar = ({username: usernameProp, className, avatarId, hasAvatar}: AvatarProps) => {
    const auth = useAuth();
    const users = useSnapshot(userState).users;
    const username = getUsername();
    const user = username === auth.state.user?.username ? auth.state.user : users.find(user => user.username === username);
    const imageId = avatarId ?? user?.avatarId;
    const showImage = hasAvatar ?? user?.hasAvatar ?? Boolean(usernameProp);

    function getUsername() {
        if (usernameProp === undefined || usernameProp === null || usernameProp == "") {
            return auth.state.user?.username;
        }

        return usernameProp;
    }

    return (
        <HeroUiAvatar className={className}>
            {/* TODO: Check if avatar can be loaded from SSO */}
            {showImage && (
                <HeroUiAvatar.Image key={`${username}:${imageId}`} src={`/images/avatar?${new URLSearchParams({username: username ?? "", v: String(imageId ?? "")})}`}/>
            )}
            <HeroUiAvatar.Fallback className={`bg-transparent ${className ?? ""}`}>
                <UserIcon/>
            </HeroUiAvatar.Fallback>
        </HeroUiAvatar>
    );
}

export default Avatar;
