import {fetchWithAuth} from "Frontend/util/utils";
import {toast} from "sonner";

export async function uploadAvatar(avatar: any) {
    const formData = new FormData();
    formData.append("file", avatar);

    const response = await fetchWithAuth("avatar/upload", formData);

    const result = await response.text();

    if (response.ok) {
        window.location.reload();
    } else {
        toast.error("Error uploading avatar", {description: result});
    }
}

export async function removeAvatar() {
    const response = await fetchWithAuth("avatar/delete")

    const result = await response.text();

    if (response.ok) {
        window.location.reload();
    } else {
        toast.error("Error removing avatar", {description: result});
    }
}

export async function removeAvatarByName(name: string) {
    const response = await fetchWithAuth("avatar/deleteByName?" + new URLSearchParams({name: name}))

    const result = await response.text();

    if (response.ok) {
        window.location.reload();
    } else {
        toast.error("Error removing avatar", {description: result});
    }
}