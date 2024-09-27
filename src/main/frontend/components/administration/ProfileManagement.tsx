import Section from "Frontend/components/general/Section";
import Input from "Frontend/components/general/Input";
import {Button, Input as NextUiInput, Tooltip} from "@nextui-org/react";
import {Form, Formik} from "formik";
import {ArrowCounterClockwise, Check, Info, Trash} from "@phosphor-icons/react";
import React, {useEffect, useState} from "react";
import {useAuth} from "Frontend/util/auth";
import * as Yup from "yup";
import UserUpdateDto from "Frontend/generated/de/grimsi/gameyfin/users/dto/UserUpdateDto";
import {EmailConfirmationEndpoint, UserEndpoint} from "Frontend/generated/endpoints";
import {SmallInfoField} from "Frontend/components/general/SmallInfoField";
import {toast} from "sonner";
import {removeAvatar, uploadAvatar} from "Frontend/endpoints/AvatarEndpoint";
import Avatar from "Frontend/components/general/Avatar";

export default function ProfileManagement() {
    const [configSaved, setConfigSaved] = useState(false);
    const auth = useAuth();
    const [avatar, setAvatar] = useState<any>();

    useEffect(() => {
        if (configSaved) {
            setTimeout(() => setConfigSaved(false), 2000);
        }
    }, [configSaved])


    function onFileSelected(event: any) {
        setAvatar(event.target.files[0]);
    }

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
                            {auth.state.user?.managedBySso &&
                                <p className="text-warning">Your account is managed externally.</p>}

                            <div className="flex flex-row items-center gap-4">
                                {formik.values.newPassword.length > 0 &&
                                    <SmallInfoField icon={Info}
                                                    message="You will be logged out of all current sessions"
                                                    className="text-foreground/70"
                                    />
                                }
                                <Button
                                    color="primary"
                                    isLoading={formik.isSubmitting}
                                    disabled={formik.isSubmitting || configSaved || auth.state.user?.managedBySso}
                                    type="submit"
                                >
                                    {formik.isSubmitting ? "" : configSaved ? <Check/> : "Save"}
                                </Button>
                            </div>
                        </div>

                        <div className="flex flex-row flex-1 justify-between gap-16">
                            <div className="flex flex-col basis-1/4 mt-8 gap-4">
                                <div className="flex flex-row justify-center">
                                    <Avatar className="size-40 m-4 flex flex-row"/>
                                </div>
                                <div className="flex flex-row gap-2">
                                    <NextUiInput type="file" accept="image/*" onChange={onFileSelected}
                                                 isDisabled={auth.state.user?.managedBySso}/>
                                    <Button onClick={() => uploadAvatar(avatar)} isDisabled={avatar == null}
                                            color="success">Upload</Button>
                                    <Tooltip content="Remove your current avatar">
                                        <Button onClick={removeAvatar} isIconOnly color="danger"
                                                isDisabled={auth.state.user?.managedBySso}><Trash/></Button>
                                    </Tooltip>
                                </div>
                            </div>

                            <div className="flex flex-col flex-grow">
                                <Section title="Personal information"/>
                                <Input name="username" label="Username" type="text" autocomplete="username"
                                       isDisabled={auth.state.user?.managedBySso}/>
                                <div className="flex flex-row gap-4">
                                    <Input name="email" label="Email" type="email" autocomplete="email"
                                           isDisabled={auth.state.user?.managedBySso}/>
                                    {auth.state.user?.emailConfirmed === false &&
                                        <Tooltip content="Resend email confirmation message">
                                            <Button isIconOnly
                                                    onPress={() => {
                                                        EmailConfirmationEndpoint.resendEmailConfirmation().then(
                                                            () => toast.success("You will receive an email shortly")
                                                        )
                                                    }}
                                                    variant="ghost"
                                                    className="size-14"
                                            >
                                                <ArrowCounterClockwise size={26}/>
                                            </Button>
                                        </Tooltip>
                                    }
                                </div>
                                <Section title="Security"/>
                                <Input name="newPassword" label="New Password" type="password"
                                       autocomplete="new-password" isDisabled={auth.state.user?.managedBySso}/>
                                <Input name="passwordRepeat" label="Repeat password" type="password"
                                       autocomplete="new-password" isDisabled={auth.state.user?.managedBySso}/>
                            </div>
                        </div>
                    </Form>
                )}
            </Formik>
        </>
    );
}