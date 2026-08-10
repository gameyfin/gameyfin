import {useAuth} from "Frontend/util/auth";
import {GearFineIcon, QuestionIcon, SignOutIcon, UserIcon} from "@phosphor-icons/react";
import {Description, Dropdown, Label} from "@heroui/react";
import {useNavigate} from "react-router";
import Avatar from "Frontend/components/general/Avatar";
import {isAdmin} from "Frontend/util/utils";

export default function ProfileMenu() {
    const auth = useAuth();
    const navigate = useNavigate();

    const profileMenuItems = [
        {
            label: "My Profile",
            icon: <UserIcon/>,
            onClick: () => navigate("/settings/profile")
        },
        {
            label: "Administration",
            icon: <GearFineIcon/>,
            onClick: () => navigate("/administration/games"),
            showIf: isAdmin(auth)
        },
        {
            label: "Help",
            icon: <QuestionIcon/>,
            onClick: () => window.open("https://gameyfin.org", "_blank")
        },
        {
            label: "Sign Out",
            icon: <SignOutIcon/>,
            onClick: auth.logout,
            color: "danger"
        },
    ];

    return (
        <Dropdown>
            {/* div is necessary so dropdown menu will appear in the correct place */}
            <div>
                <Avatar className="transition-transform size-8 gradient-primary"/>
            </div>
            <Dropdown.Popover placement="bottom end">
                <Dropdown.Menu disabledKeys={["username"]} aria-label="Profile menu">
                    <Dropdown.Item key="username" id="username" textValue={auth.state.user?.username}>
                        <Label className="font-bold">Signed in as {auth.state.user?.username}</Label>
                    </Dropdown.Item>
                    {profileMenuItems.filter(item => item.showIf !== false).map(({label, icon, onClick, color}) => {
                        return (
                            <Dropdown.Item
                                key={label}
                                id={label}
                                onAction={onClick}
                                className={color ? `text-${color}` : ""}
                                textValue={label}
                            >
                                <div className="flex items-center gap-2">
                                    {icon}
                                    <Label>{label}</Label>
                                </div>
                            </Dropdown.Item>
                        );
                    })}
                </Dropdown.Menu>
            </Dropdown.Popover>
        </Dropdown>
    );
}