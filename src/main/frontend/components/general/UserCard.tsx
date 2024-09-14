import UserInfoDto from "Frontend/generated/de/grimsi/gameyfin/users/dto/UserInfoDto";
import {Avatar, Card, Chip} from "@nextui-org/react";
import {roleToColor, roleToRoleName} from "Frontend/util/utils";

export function UserCard({user}: { user: UserInfoDto }) {
    return (
        <Card className="flex flex-row items-center gap-4 p-2">
            <Avatar showFallback
                    name={user.username?.charAt(0)}
                    src={`/images/avatar?username=${user?.username}`}
                    classNames={{
                        base: "gradient-primary size-20",
                        icon: "text-background/80",
                        name: "text-background/80 text-5xl -mt-1",
                    }}></Avatar>
            <div className="flex flex-col gap-1">
                <p className="font-semibold">{user.username}</p>
                <p className="text-sm">{user.email}</p>
                {user.roles?.map((role) =>
                    <Chip key={role} size="sm" radius="sm"
                          className={`text-xs bg-${roleToColor(role!)}-500`}>{roleToRoleName(role!)}</Chip>)}
            </div>
        </Card>
    )
}