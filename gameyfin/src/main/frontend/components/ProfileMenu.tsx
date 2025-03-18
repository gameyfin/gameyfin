import {useAuth} from "Frontend/util/auth";
import {GearFine, Question, SignOut, User} from "@phosphor-icons/react";
import {Dropdown, DropdownItem, DropdownMenu, DropdownTrigger} from "@heroui/react";
import {useNavigate} from "react-router-dom";
import {ConfigEndpoint} from "Frontend/generated/endpoints";
import Avatar from "Frontend/components/general/Avatar";
import {CollectionElement} from "@react-types/shared";

export default function ProfileMenu() {
    const auth = useAuth();
    const navigate = useNavigate();

    async function logout() {
        if (auth.state.user?.managedBySso) {
            window.location.href = (await ConfigEndpoint.getLogoutUrl()) || "/";
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

    // @ts-ignore
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
            <DropdownMenu disabledKeys={["username"]}>
                <DropdownItem key="username">
                    <p className="font-bold">Signed in as {auth.state.user?.username}</p>
                </DropdownItem>
                {profileMenuItems.filter(item => item.showIf !== false).map(({label, icon, onClick, color}) => {
                    return (
                        <DropdownItem
                            key={label}
                            onPress={onClick}
                            startContent={<div color={color}>{icon}</div>}
                            /* @ts-ignore */
                            color={color ? color : ""}
                            className={`text-${color} hover:bg-primary/20`}
                        >
                            {label}
                        </DropdownItem>
                    );
                }) as unknown as CollectionElement<object>}
            </DropdownMenu>
        </Dropdown>
    );
}