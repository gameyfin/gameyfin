import {Chip} from "@heroui/react";
import {roleToColor, roleToRoleName} from "Frontend/util/utils";

export default function RoleChip({role}: { role: string }) {
    return (
        <Chip key={role} size="sm" radius="sm" className={`text-xs ${roleToColor(role)}`}>
            {roleToRoleName(role)}
        </Chip>
    );
}