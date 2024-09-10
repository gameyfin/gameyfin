import {Outlet, useNavigate} from "react-router-dom";
import {Envelope, GameController, Users} from "@phosphor-icons/react";
import {Listbox, ListboxItem} from "@nextui-org/react";

export default function AdministrationView() {
    const navigate = useNavigate();

    const menuItems = [
        {
            title: "Libraries",
            key: "libraries",
            icon: <GameController/>,
            action: () => navigate('libraries')
        },
        {
            title: "Users",
            key: "users",
            icon: <Users/>,
            action: () => navigate('users')
        },
        {
            title: "Notifications",
            icon: <Envelope/>,
            key: "notifications",
            action: () => navigate('notifications')
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