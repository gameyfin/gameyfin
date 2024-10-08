import {Chip} from "@nextui-org/react";
import {roleToColor, roleToRoleName} from "Frontend/util/utils";

export default function RoleChip({role}: { role: string }) {
    return (
        <Chip key={role} size="sm" radius="sm" className={`text-xs bg-${roleToColor(role)}-500`}>
            {roleToRoleName(role)}
        </Chip>
    );
}