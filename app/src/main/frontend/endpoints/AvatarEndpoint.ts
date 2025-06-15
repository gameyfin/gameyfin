import {fetchWithAuth} from "Frontend/util/utils";
import {addToast} from "@heroui/react";

export async function uploadAvatar(avatar: any) {
    const formData = new FormData();
    formData.append("file", avatar);

    const response = await fetchWithAuth("images/avatar/upload", formData);

    const result = await response.text();

    if (response.ok) {
        window.location.reload();
    } else {
        addToast({
            title: "Error uploading avatar",
            description: result,
            color: "danger"
        });
    }
}

export async function removeAvatar() {
    const response = await fetchWithAuth("images/avatar/delete")

    const result = await response.text();

    if (response.ok) {
        window.location.reload();
    } else {
        addToast({
            title: "Error removing avatar",
            description: result,
            color: "danger"
        });
    }
}

export async function removeAvatarByName(name: string) {
    const response = await fetchWithAuth("images/avatar/deleteByName?" + new URLSearchParams({name: name}))

    const result = await response.text();

    if (response.ok) {
        window.location.reload();
    } else {
        addToast({
            title: "Error removing avatar",
            description: result,
            color: "danger"
        });
    }
}