import {useState} from "react";
import {useAuth} from "Frontend/util/auth";
import {useNavigate} from "react-router-dom";
import {GearFine, Question, SignOut, User} from "@phosphor-icons/react";
import {
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuTrigger
} from "Frontend/@/components/ui/dropdown-menu";
import {Avatar, AvatarFallback} from "Frontend/@/components/ui/avatar";

export default function ProfileMenu() {
    const [isMenuOpen, setIsMenuOpen] = useState(false);
    const {state, logout} = useAuth();
    const navigate = useNavigate();

    const profileMenuItems = [
        {
            label: "My Profile",
            icon: <User/>,
            onClick: () => alert("Profile")
        },
        {
            label: "Administration",
            icon: <GearFine/>,
            onClick: () => alert("Administration"),
            showIf: state.user?.authorities?.some(a => a?.includes("ADMIN"))
        },
        {
            label: "Help",
            icon: <Question/>,
            onClick: () => window.open("https://github.com/gameyfin/gameyfin/tree/v2", "_blank")
        },
        {
            label: "Sign Out",
            icon: <SignOut/>,
            onClick: () => logout(),
            color: "red-500"
        },
    ];

    return (
        <DropdownMenu open={isMenuOpen}>
            <DropdownMenuTrigger>
                <Avatar>
                    <AvatarFallback>{state.user?.name?.substring(0, 2).toUpperCase()}</AvatarFallback>
                </Avatar>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
                {profileMenuItems.map(({label, icon, onClick, showIf, color}) => {
                    return (
                        (showIf === undefined || showIf === true) ?
                            <DropdownMenuItem key={label} onClick={onClick}>
                                {icon}
                                <p color={color ? color : ""}>{label}</p>
                            </DropdownMenuItem> : null
                    );
                })}
            </DropdownMenuContent>
        </DropdownMenu>
    );
}