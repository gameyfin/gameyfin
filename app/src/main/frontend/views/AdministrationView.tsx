import {
    DiscIcon,
    DownloadSimpleIcon,
    EnvelopeIcon,
    GameControllerIcon,
    LayoutIcon,
    LockKeyIcon,
    LogIcon,
    PlugIcon,
    UsersIcon,
    WrenchIcon
} from "@phosphor-icons/react";
import withSideMenu, {MenuItem} from "Frontend/components/general/withSideMenu";

const menuItems: MenuItem[] = [
    {
        title: "Libraries",
        url: "libraries",
        icon: <GameControllerIcon/>
    },
    {
        title: "UI Settings",
        url: "ui",
        icon: <LayoutIcon/>
    },
    {
        title: "Game Requests",
        url: "requests",
        icon: <DiscIcon/>
    },
    {
        title: "Downloads",
        url: "downloads",
        icon: <DownloadSimpleIcon/>
    },
    {
        title: "Users",
        url: "users",
        icon: <UsersIcon/>
    },
    {
        title: "Security",
        url: "security",
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