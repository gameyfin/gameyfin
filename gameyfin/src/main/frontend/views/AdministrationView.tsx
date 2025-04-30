import {Envelope, GameController, LockKey, Log, Plug, Users, Wrench} from "@phosphor-icons/react";
import withSideMenu, {MenuItem} from "Frontend/components/general/withSideMenu";

const menuItems: MenuItem[] = [
    {
        title: "Libraries",
        url: "libraries",
        icon: <GameController/>
    },
    {
        title: "Users",
        url: "users",
        icon: <Users/>
    },
    {
        title: "SSO",
        url: "sso",
        icon: <LockKey/>
    },
    {
        title: "Messages",
        url: "messages",
        icon: <Envelope/>
    },
    {
        title: "Plugins",
        url: "plugins",
        icon: <Plug/>
    },
    {
        title: "Logs",
        url: "logs",
        icon: <Log/>
    },
    {
        title: "System",
        url: "system",
        icon: <Wrench/>
    }
]

export const AdministrationView = withSideMenu(menuItems);
export default AdministrationView;