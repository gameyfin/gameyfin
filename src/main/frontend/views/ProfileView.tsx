import {GearFine, Palette, User} from "@phosphor-icons/react";
import withSideMenu from "Frontend/components/general/withSideMenu";

const menuItems = [
    {
        title: "My Profile",
        url: "profile",
        icon: <User/>
    },
    {
        title: "Appearance",
        url: "appearance",
        icon: <Palette/>
    },
    {
        title: "Manage account",
        url: "account-management",
        icon: <GearFine/>
    }
]

export const ProfileView = withSideMenu(menuItems);