import { DiscIcon, EnvelopeIcon, GameControllerIcon, LockKeyIcon, LogIcon, PlugIcon, UsersIcon, WrenchIcon } from "@phosphor-icons/react";
import withSideMenu, {MenuItem} from "Frontend/components/general/withSideMenu";

const menuItems: MenuItem[] = [
    {
        title: "Libraries",
        url: "libraries",
        icon: <GameControllerIcon/>
    },
    {
        title: "Game Requests",
        url: "requests",
        icon: <DiscIcon/>
    },
    {
        title: "UsersIcon",
        url: "users",
        icon: <UsersIcon/>
    },
    {
        title: "SSO",
        url: "sso",
        icon: <LockKeyIcon/>
    },
    {
        title: "Messages",
        url: "messages",
        icon: <EnvelopeIcon/>
    },
    {
        title: "Plugins",
        url: "plugins",
        icon: <PlugIcon/>
    },
    {
        title: "Logs",
        url: "logs",
        icon: <LogIcon/>
    },
    {
        title: "System",
        url: "system",
        icon: <WrenchIcon/>
    }
]

export const AdministrationView = withSideMenu("/administration", menuItems);
export default AdministrationView;