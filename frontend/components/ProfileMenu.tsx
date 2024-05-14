import {useAuth} from "Frontend/util/auth";
import {useNavigate} from "react-router-dom";
import {GearFine, Question, SignOut, User} from "@phosphor-icons/react";
import {Avatar, Dropdown, DropdownItem, DropdownMenu, DropdownTrigger} from "@nextui-org/react";

export default function ProfileMenu() {
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
            color: "danger"
        },
    ];

    return (
        <Dropdown placement="bottom-end">
            <DropdownTrigger>
                <Avatar showFallback radius="full" as="button" className="transition-transform"/>
            </DropdownTrigger>
            <DropdownMenu>
                {/* @ts-ignore */}
                {profileMenuItems.map(({label, icon, onClick, showIf, color}) => {
                    return (
                        (showIf === undefined || showIf === true) ?
                            <DropdownItem
                                key={label}
                                onClick={onClick}
                                startContent={<div color={color}>{icon}</div>}
                                /* @ts-ignore */
                                color={color ? color : ""}
                                className={`text-${color}`}
                            >
                                {label}
                            </DropdownItem> : null
                    );
                })}
            </DropdownMenu>
        </Dropdown>
    );
}