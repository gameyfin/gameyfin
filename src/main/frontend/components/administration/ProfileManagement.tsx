import Section from "Frontend/components/general/Section";
import Input from "Frontend/components/general/Input";
import {Form, Formik} from "formik";
import {Avatar, Button} from "@nextui-org/react";
import {Check, Info} from "@phosphor-icons/react";
import React, {useEffect, useState} from "react";
import {useAuth} from "Frontend/util/auth";
import * as Yup from "yup";
import UserUpdateDto from "Frontend/generated/de/grimsi/gameyfin/users/dto/UserUpdateDto";
import {UserEndpoint} from "Frontend/generated/endpoints";
import {SmallInfoField} from "Frontend/components/general/SmallInfoField";
import {toast} from "sonner";
import AvatarUpload from "Frontend/components/general/AvatarUpload";

export default function ProfileManagement() {
    const [configSaved, setConfigSaved] = useState(false);
    const auth = useAuth();

    useEffect(() => {
        if (configSaved) {
            setTimeout(() => setConfigSaved(false), 2000);
        }
    }, [configSaved])

    async function handleSubmit(values: any) {
        const userUpdate: UserUpdateDto = {
            username: values.username,
            email: values.email
        }

        if (values.newPassword.length > 0) {
            userUpdate.password = values.newPassword;
        }

        await UserEndpoint.updateUser(userUpdate);
        setConfigSaved(true);

        if (values.newPassword.length > 0) {
            toast.success("Password changed", {
                description: "Please log in again"
            });
            setTimeout(() => {
                auth.logout();
            }, 500);
        }
    }

    return (
        <>
            <Formik
                initialValues={{
                    username: auth.state.user?.username,
                    email: auth.state.user?.email,
                    newPassword: "",
                    passwordRepeat: ""
                }}
                onSubmit={handleSubmit}
                validationSchema={Yup.object({
                    username: Yup.string()
                        .required('Required'),
                    newPassword: Yup.string()
                        .min(8, 'Password must be at least 8 characters long'),
                    email: Yup.string()
                        .email()
                        .required('Required'),
                    passwordRepeat: Yup.string()
                        .equals([Yup.ref('newPassword')], 'Passwords do not match')
                })}
            >
                {(formik: { values: any; isSubmitting: any; }) => (
                    <Form>
                        <div className="flex flex-row flex-grow justify-between mb-8">
                            <h2 className="text-2xl font-bold">My Profile</h2>

                            <div className="flex flex-row items-center gap-4">
                                {formik.values.newPassword.length > 0 &&
                                    <SmallInfoField icon={Info}
                                                    message="You will be logged out of all current sessions"
                                                    className="text-foreground/70"
                                    />
                                }
                                <Button
                                    className="button-secondary"
                                    isLoading={formik.isSubmitting}
                                    disabled={formik.isSubmitting || configSaved}
                                    type="submit"
                                >
                                    {formik.isSubmitting ? "" : configSaved ? <Check/> : "Save"}
                                </Button>
                            </div>
                        </div>

                        <div className="flex flex-row flex-1 justify-between gap-16">
                            <div className="flex flex-col basis-1/4 mt-8 items-center">
                                <Avatar showFallback
                                        src={`/images/avatar?username=${auth.state.user?.username}`}
                                        className="size-40 m-4"></Avatar>
                                <AvatarUpload upload="/avatar/upload" remove="/avatar/delete" accept="image/*"/>
                            </div>

                            <div className="flex flex-col flex-grow">
                                <Section title="Personal information"/>
                                <Input name="username" label="Username" type="text" autocomplete="username"/>
                                <Input name="email" label="Email" type="email" autocomplete="email"/>
                                <Section title="Security"/>
                                <Input name="newPassword" label="New Password" type="password"
                                       autocomplete="new-password"/>
                                <Input name="passwordRepeat" label="Repeat password" type="password"
                                       autocomplete="new-password"/>
                            </div>
                        </div>
                    </Form>
                )}
            </Formik>
        </>
    );
}