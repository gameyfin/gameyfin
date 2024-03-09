import {useState} from "react";
import {Button, Menu, MenuHandler, MenuItem, MenuList} from "@material-tailwind/react";
import {useAuth} from "Frontend/util/auth";
import {Avatar} from "@hilla/react-components/Avatar";
import {useNavigate} from "react-router-dom";
import {CaretDown, CaretUp, GearFine, IconContext, Question, SignOut, User} from "@phosphor-icons/react";

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
        <Menu open={isMenuOpen} handler={setIsMenuOpen} placement="bottom-end">
            <MenuHandler>
                <Button
                    variant="text"
                    className="flex items-center gap-1 rounded-full py-0.5 pr-2 pl-0.5 lg:ml-auto"
                >
                    <Avatar
                        name={state.user?.name}
                        abbr={state.user?.name?.substring(0, 2)}
                    />
                    <IconContext.Provider value={{size: 12}}>
                        {isMenuOpen ? <CaretUp/> : <CaretDown/>}
                    </IconContext.Provider>
                </Button>
            </MenuHandler>
            <MenuList className="p-1">
                {profileMenuItems.map(({label, icon, onClick, showIf, color}) => {
                    return (
                        (showIf === undefined || showIf === true) ?
                            <MenuItem
                                key={label}
                                onClick={onClick}
                                className={`flex items-center gap-2 rounded ${
                                    color ? `hover:${color}/10 focus:${color}/10 active:${color}/10` : ""
                                }`}
                            >
                                {icon}
                                <p color={color ? color : ""}>{label}</p>
                            </MenuItem> : null
                    );
                })}
            </MenuList>
        </Menu>
    );
}