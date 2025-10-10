import {useAuth} from "Frontend/util/auth";
import { GearFineIcon, QuestionIcon, SignOutIcon, UserIcon } from "@phosphor-icons/react";
import {Dropdown, DropdownItem, DropdownMenu, DropdownTrigger} from "@heroui/react";
import {useNavigate} from "react-router";
import Avatar from "Frontend/components/general/Avatar";
import {CollectionElement} from "@react-types/shared";
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
            onClick: () => navigate("/administration/libraries"),
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
                <DropdownItem key="username" textValue={auth.state.user?.username}>
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
                            textValue={label}
                        >
                            {label}
                        </DropdownItem>
                    );
                }) as unknown as CollectionElement<object>}
            </DropdownMenu>
        </Dropdown>
    );
}