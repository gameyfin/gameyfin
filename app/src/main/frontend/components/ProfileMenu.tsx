import {useProfileActions} from "Frontend/components/layout/useProfileActions";
import {useAuth} from "Frontend/util/auth";
import {Dropdown, Label} from "@heroui/react";
import Avatar from "Frontend/components/general/Avatar";

export default function ProfileMenu() {
    const auth = useAuth();
    const profileMenuItems = useProfileActions();

    return (
        <Dropdown>
            <Dropdown.Trigger aria-label="Profile menu" className="rounded-full">
                <Avatar className="transition-transform size-8 rounded-full gradient-primary text-accent-foreground"/>
            </Dropdown.Trigger>
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
