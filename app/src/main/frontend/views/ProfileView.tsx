import {GearFineIcon, KeyIcon, PaletteIcon, UserIcon} from "@phosphor-icons/react";
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
    {
        title: "Personal access tokens",
        url: "tokens",
        icon: <KeyIcon/>
    },
    {
        title: "Manage account",
        url: "account-management",
        icon: <GearFineIcon/>
    }
]

export const ProfileView = withSideMenu("/settings", menuItems);
export default ProfileView;
