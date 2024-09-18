import {useAuth} from "Frontend/util/auth";
import {GearFine, Question, SignOut, User} from "@phosphor-icons/react";
import {Dropdown, DropdownItem, DropdownMenu, DropdownTrigger} from "@nextui-org/react";
import {useNavigate} from "react-router-dom";
import {ConfigEndpoint} from "Frontend/generated/endpoints";
import Avatar from "Frontend/components/general/Avatar";

export default function ProfileMenu() {
    const auth = useAuth();
    const navigate = useNavigate();

    async function logout() {
        if (auth.state.user?.managedBySso) {
            window.location.href = await ConfigEndpoint.getLogoutUrl() || "/";
        } else {
            await auth.logout();
        }
    }

    const profileMenuItems = [
        {
            label: "My Profile",
            icon: <User/>,
            onClick: () => navigate('/settings/profile')
        },
        {
            label: "Administration",
            icon: <GearFine/>,
            onClick: () => navigate("/administration/libraries"),
            showIf: auth.state.user?.roles?.some(a => a?.includes("ADMIN"))
        },
        {
            label: "Help",
            icon: <Question/>,
            onClick: () => window.open("https://github.com/gameyfin/gameyfin/tree/v2", "_blank")
        },
        {
            label: "Sign Out",
            icon: <SignOut/>,
            onClick: logout,
            color: "primary"
        },
    ];

    return (
        <Dropdown placement="bottom-end">
            <DropdownTrigger>
                {/* div is necessary so dropdown menu will appear in the correct place */}
                <div>
                    <Avatar radius="full"
                            as="button"
                            className="transition-transform size-8"
                            classNames={{
                                base: "gradient-primary",
                                icon: "text-background/80"
                            }}
                    />
                </div>
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
                                className={`text-${color} hover:bg-primary/20`}
                            >
                                {label}
                            </DropdownItem> : null
                    );
                })}
            </DropdownMenu>
        </Dropdown>
    );
}