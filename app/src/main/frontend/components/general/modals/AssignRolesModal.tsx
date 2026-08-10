import React, {useEffect, useState} from "react";
import {
    Button,
    ListBox,
    Modal,
    Select,
} from "@heroui/react";
import {UserEndpoint} from "Frontend/generated/endpoints";
import RoleChip from "Frontend/components/general/RoleChip";
import RoleAssignmentResult from "Frontend/generated/org/gameyfin/app/users/enums/RoleAssignmentResult";
import ExtendedUserInfoDto from "Frontend/generated/org/gameyfin/app/users/dto/ExtendedUserInfoDto";

interface AssignRolesModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    user: ExtendedUserInfoDto;
}

interface Role {
    id: string;
}

export default function AssignRolesModal({isOpen, onOpenChange, user}: AssignRolesModalProps) {
    const [availableRoles, setAvailableRoles] = useState<Role[]>([]);
    const [selectedRoles, setSelectedRoles] = useState<string[]>([]);
    const [error, setError] = useState<string>();

    useEffect(() => {
        setSelectedRoles(rolesToSelection(user.roles));
        UserEndpoint.getRolesBelow().then((availableRoles) => {
            setAvailableRoles(availableRoles.map((role) => ({id: role.toString()})));
        });
    }, []);

    function rolesToSelection(roles: Array<string>): string[] {
        return roles.map((role) => role.toString());
    }

    async function assignRoles() {
        if (selectedRoles.length === 0) return;

        let result = await UserEndpoint.assignRoles(user.username, selectedRoles);
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
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} variant="opaque" isDismissable={false}>
                <Modal.Container size="lg">
                    <Modal.Dialog>
                        {({close}) => (
                            <>
                                <Modal.Header className="flex flex-col gap-1">
                                    <Modal.Heading>Assign roles to {user.username}</Modal.Heading>
                                </Modal.Header>
                                <Modal.Body className="flex flex-col gap-2">
                                    <Select
                                        selectionMode="multiple"
                                        value={selectedRoles}
                                        onChange={(value) => setSelectedRoles((value as string[]) ?? [])}
                                        placeholder="Select roles"
                                    >
                                        <Select.Trigger>
                                            <Select.Value/>
                                            <Select.Indicator/>
                                        </Select.Trigger>
                                        <Select.Popover>
                                            <ListBox>
                                                {availableRoles.map((role) => (
                                                    <ListBox.Item key={role.id} id={role.id} textValue={role.id}>
                                                        <RoleChip role={role.id}/>
                                                        <ListBox.ItemIndicator/>
                                                    </ListBox.Item>
                                                ))}
                                            </ListBox>
                                        </Select.Popover>
                                    </Select>
                                    <div className="flex grow flex-wrap gap-2">
                                        {selectedRoles.map((role) => (
                                            <RoleChip key={role} role={role}/>
                                        ))}
                                    </div>
                                    {error &&
                                        <small className="text-danger">{error}</small>
                                    }
                                </Modal.Body>
                                <Modal.Footer>
                                    <Button variant="tertiary" onPress={close}>
                                        Cancel
                                    </Button>
                                    <Button variant="primary" onPress={assignRoles} isDisabled={selectedRoles.length === 0}>
                                        Assign roles
                                    </Button>
                                </Modal.Footer>
                            </>
                        )}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}