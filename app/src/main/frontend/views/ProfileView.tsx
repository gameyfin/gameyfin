import { PaletteIcon, UserIcon } from "@phosphor-icons/react";
import withSideMenu from "Frontend/components/general/withSideMenu";

const menuItems = [
    {
        title: "My Profile",
        url: "profile",
        icon: <UserIcon/>
    },
    {
        title: "Appearance",
        url: "appearance",
        icon: <PaletteIcon/>
    },
    /* TODO: Implement account self management
    {
        title: "Manage account",
        url: "account-management",
        icon: <GearFine/>
    }*/
]

export const ProfileView = withSideMenu("/settings", menuItems);
export default ProfileView;