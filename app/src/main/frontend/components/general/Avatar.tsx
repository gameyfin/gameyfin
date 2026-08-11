import {useAuth} from "Frontend/util/auth";
import {Avatar as HeroUiAvatar} from "@heroui/react";
import {UserIcon} from "@phosphor-icons/react";

interface AvatarProps {
    username?: string;
    className?: string;
}

const Avatar = ({username: usernameProp, className}: AvatarProps) => {
    const auth = useAuth();
    const username = getUsername();

    function getUsername() {
        if (usernameProp === undefined || usernameProp === null || usernameProp == "") {
            return auth.state.user?.username;
        }

        return usernameProp;
    }

    return (
        <HeroUiAvatar className={className}>
            {/* TODO: Check if avatar can be loaded from SSO */}
            {auth.state.user?.hasAvatar && (
                <HeroUiAvatar.Image src={`/images/avatar?username=${username}`}/>
            )}
            {/* Fallback has its own opaque background (avatar__fallback) that fully covers the
                root Avatar's background, so any custom background (e.g. gradient-primary) passed
                via className must also be forwarded here to be visible. */}
            <HeroUiAvatar.Fallback className={`bg-transparent ${className ?? ""}`}>
                {/*{username?.charAt(0).toUpperCase()}*/}
                <UserIcon/>
            </HeroUiAvatar.Fallback>
        </HeroUiAvatar>
    );
}

export default Avatar;