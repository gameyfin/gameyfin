import {useAuth} from "Frontend/util/auth";
import {Avatar as HeroUiAvatar} from "@heroui/react";

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
        <HeroUiAvatar.Root className={className}>
            {/* TODO: Check if avatar can be loaded from SSO */}
            {auth.state.user?.hasAvatar && (
                <HeroUiAvatar.Image src={`/images/avatar?username=${username}`}/>
            )}
            <HeroUiAvatar.Fallback>
                {username?.charAt(0).toUpperCase()}
            </HeroUiAvatar.Fallback>
        </HeroUiAvatar.Root>
    );
}

export default Avatar;