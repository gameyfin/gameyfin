import {Envelope, GameController, LockKey, Log, Users} from "@phosphor-icons/react";
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
        title: "Notifications",
        url: "notifications",
        icon: <Envelope/>
    },
    {
        title: "Logs",
        url: "logs",
        icon: <Log/>
    }
]

export const AdministrationView = withSideMenu(menuItems);