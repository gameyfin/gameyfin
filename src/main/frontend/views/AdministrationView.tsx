import {Envelope, GameController, LockKey, Users} from "@phosphor-icons/react";
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
    }
]

export const AdministrationView = withSideMenu(menuItems);