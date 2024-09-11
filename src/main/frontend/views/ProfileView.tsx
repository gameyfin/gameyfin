import {Listbox, ListboxItem} from "@nextui-org/react";
import {GearFine, Palette, User} from "@phosphor-icons/react";
import {Outlet, useNavigate} from "react-router-dom";

export default function ProfileView() {
    const navigate = useNavigate();

    const menuItems = [
        {
            title: "My Profile",
            key: "profile",
            icon: <User/>,
            action: () => navigate('profile')
        },
        {
            title: "Appearance",
            key: "appearance",
            icon: <Palette/>,
            action: () => navigate('appearance')
        },
        {
            title: "Manage account",
            icon: <GearFine/>,
            key: "account-management",
            action: () => navigate('account-management')
        }
    ]

    return (
        <div className="flex flex-row">
            <div className="flex flex-col pr-8">
                <Listbox className="min-w-60">
                    {menuItems.map((i) => (
                        <ListboxItem key={i.key} onPress={i.action} startContent={i.icon}>
                            {i.title}
                        </ListboxItem>
                    ))}
                </Listbox>
            </div>
            <div className="flex flex-col flex-grow">
                <Outlet/>
            </div>
        </div>
    );
}