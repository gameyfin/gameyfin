import React, {useEffect, useState} from "react";
import {
    Button,
    Modal,
    ModalBody,
    ModalContent,
    ModalFooter,
    ModalHeader,
    Select,
    SelectedItems,
    Selection,
    SelectItem
} from "@nextui-org/react";
import {UserEndpoint} from "Frontend/generated/endpoints";
import UserInfoDto from "Frontend/generated/de/grimsi/gameyfin/users/dto/UserInfoDto";
import RoleChip from "Frontend/components/general/RoleChip";
import RoleAssignmentResult from "Frontend/generated/de/grimsi/gameyfin/users/enums/RoleAssignmentResult";

interface AssignRolesModalProps {
    isOpen: boolean;
    onOpenChange: () => void;
    user: UserInfoDto;
}

interface Role {
    id: string;
}

export default function AssignRolesModal({
                                             isOpen,
                                             onOpenChange,
                                             user
                                         }: AssignRolesModalProps) {
    const [availableRoles, setAvailableRoles] = useState<Role[]>([]);
    const [selectedRole, setSelectedRole] = useState<Selection>();
    const [error, setError] = useState<string>();

    useEffect(() => {
        setSelectedRole(rolesToSelection(user.roles!));
        UserEndpoint.getRolesBelow().then((availableRoles) => {
            setAvailableRoles(availableRoles!.map((role) => ({id: role!.toString()})));
        });
    }, []);

    function rolesToSelection(roles: Array<string | undefined>): Selection {
        return new Set(roles.map((role) => role!.toString()));
    }

    async function assignRoles() {
        if (!selectedRole) return;

        let selectedRolesArray = Array.from(selectedRole).map((role) => role.toString());
        let result = await UserEndpoint.assignRoles(user.username, selectedRolesArray);
        if (!result) return;
        switch (result) {
            case RoleAssignmentResult.SUCCESS:
                window.location.reload();
                break;
            case RoleAssignmentResult.NO_ROLES_PROVIDED:
                setError("Select at least one role");
                break;
            case RoleAssignmentResult.TARGET_POWER_LEVEL_TOO_HIGH:
                setError("Power level of user too high");
                break;
            case RoleAssignmentResult.ASSIGNED_ROLE_POWER_LEVEL_TOO_HIGH:
                setError("Power level of assigned role too high");
                break;
            default:
                setError("An error occurred");
                break;
        }
    }

    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="opaque" isDismissable={false}
               hideCloseButton={true} size="lg">
            <ModalContent>
                {(onClose) => (
                    <>
                        <ModalHeader className="flex flex-col gap-1">Assign roles to {user.username}</ModalHeader>
                        <ModalBody className="flex flex-col gap-2">
                            <Select
                                items={availableRoles}
                                selectionMode="single"
                                disallowEmptySelection={true}
                                selectedKeys={selectedRole}
                                onSelectionChange={setSelectedRole}
                                placeholder="Select roles"
                                renderValue={(items: SelectedItems<Role>) => {
                                    return (
                                        <div className="flex flex-grow flex-wrap gap-2">
                                            {items.map((item) => (
                                                <RoleChip key={item.key} role={item.textValue as string}/>
                                            ))}
                                        </div>
                                    );
                                }}
                            >
                                {(role) => (
                                    <SelectItem key={role.id} textValue={role.id}>
                                        <RoleChip key={role.id} role={role.id}/>
                                    </SelectItem>
                                )}
                            </Select>
                            {error &&
                                <small className="text-danger">{error}</small>
                            }
                        </ModalBody>
                        <ModalFooter>
                            <Button variant="light" onPress={onClose}>
                                Cancel
                            </Button>
                            <Button color="primary" onPress={assignRoles} isDisabled={!selectedRole}>
                                Assign roles
                            </Button>
                        </ModalFooter>
                    </>
                )}
            </ModalContent>
        </Modal>
    );
}