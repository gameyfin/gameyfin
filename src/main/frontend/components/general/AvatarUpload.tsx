import {toast} from "sonner";
import {getCsrfToken} from "Frontend/util/auth";
import {Button, Input, Tooltip} from "@nextui-org/react";
import {useState} from "react";
import {Trash} from "@phosphor-icons/react";

export default function AvatarUpload({upload, remove, accept}: { upload: string, remove: string, accept: string }) {

    const [avatar, setAvatar] = useState<any>();

    function onFileSelected(event: any) {
        setAvatar(event.target.files[0]);
    }

    async function uploadAvatar() {
        const formData = new FormData();

        formData.append("file", avatar);
        try {
            const response = await fetch(upload, {
                headers: {
                    "X-CSRF-Token": getCsrfToken()
                },
                method: "POST",
                credentials: "same-origin",
                body: formData
            });

            const result = await response.text();

            if (response.ok) {
                window.location.reload();
            } else {
                toast.error("Error uploading avatar", {description: result});
            }
        } catch (error: any) {
            toast.error("Error uploading avatar", {description: error.message})
        }
    }

    async function removeAvatar() {
        try {
            const response = await fetch(remove, {
                headers: {
                    "X-CSRF-Token": getCsrfToken()
                },
                method: "POST",
                credentials: "same-origin"
            });

            const result = await response.text();

            if (response.ok) {
                window.location.reload();
            } else {
                toast.error("Error removing avatar", {description: result});
            }
        } catch (error: any) {
            toast.error("Error removing avatar", {description: error.message})
        }
    }

    return (
        <div className="flex flex-col gap-2">
            <div className="flex flex-row gap-2">
                <Input type="file" accept={accept} onChange={onFileSelected}/>
                <Button onClick={uploadAvatar} isDisabled={avatar == null} color="success">Upload</Button>
                <Tooltip content="Remove your current avatar">
                    <Button onClick={removeAvatar} isIconOnly color="danger"><Trash/></Button>
                </Tooltip>
            </div>
        </div>
    );
};