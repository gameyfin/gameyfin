import {navigationVisibility} from "./navigationVisibility";
import {useAuth} from "Frontend/util/auth";
import {GearFineIcon, QuestionIcon, SignOutIcon, SignInIcon, UserIcon} from "@phosphor-icons/react";
import {useNavigate} from "react-router";
import {isAdmin} from "Frontend/util/utils";

export function useProfileActions() {
    const auth = useAuth();
    const navigate = useNavigate();

    const visible = navigationVisibility(!!auth.state.user, !!isAdmin(auth));
    return [
        {
            label: "My Profile",
            path: "/settings",
            icon: <UserIcon/>,
            onClick: () => navigate("/settings/profile"),
            showIf: visible.profile
        },
        {
            label: "Administration",
            path: "/administration",
            icon: <GearFineIcon/>,
            onClick: () => navigate("/administration/games"),
            showIf: visible.administration
        },
        {
            label: "Help",
            icon: <QuestionIcon/>,
            onClick: () => window.open("https://gameyfin.org", "_blank", "noopener,noreferrer")
        },
        {
            label: "Sign Out",
            icon: <SignOutIcon/>,
            onClick: auth.logout,
            showIf: visible.signOut,
            color: "danger"
        },
        {label: "Sign in", icon: <SignInIcon/>, onClick: () => {window.location.href = "/loginredirect";}, showIf: visible.signIn},
    ].filter(item => item.showIf !== false);

}
