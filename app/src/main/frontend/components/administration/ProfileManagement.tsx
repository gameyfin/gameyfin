import Section from "Frontend/components/general/Section";
import Input from "Frontend/components/general/input/Input";
import {Button, Spinner, toast, Tooltip} from "@heroui/react";
import UploadButton from "Frontend/components/general/input/UploadButton";
import {Form, FormikHelpers, FormikProvider, useFormik} from "formik";
import {ArrowCounterClockwiseIcon, CheckIcon, InfoIcon, TrashIcon} from "@phosphor-icons/react";
import React, {useEffect, useState} from "react";
import {useAuth} from "Frontend/util/auth";
import * as Yup from "yup";
import UserUpdateDto from "Frontend/generated/org/gameyfin/app/users/dto/UserUpdateDto";
import {EmailConfirmationEndpoint, MessageEndpoint, RecommendationEndpoint, UserEndpoint} from "Frontend/generated/endpoints";
import {SmallInfoField} from "Frontend/components/general/SmallInfoField";
import {removeAvatar, uploadAvatar} from "Frontend/endpoints/AvatarEndpoint";
import Avatar from "Frontend/components/general/Avatar";
import RecommendationSharing from "Frontend/components/RecommendationSharing";

interface ProfileFormValues {
    username: string | undefined;
    email: string | undefined;
    newPassword: string;
    passwordRepeat: string;
    sharesActivity?: boolean;
}

export default function ProfileManagement() {
    const auth = useAuth();
    return <ProfileForm key={auth.state.user?.id} auth={auth}/>;
}

function ProfileForm({auth}: {auth: ReturnType<typeof useAuth>}) {
    const user = auth.state.user;
    const [uploadingAvatar, setUploadingAvatar] = useState(false);
    const [configSaved, setConfigSaved] = useState(false);
    const [messagesEnabled, setMessagesEnabled] = useState(false);
    const [savedSharing, setSavedSharing] = useState<boolean>();
    const [sharingLoadFailed, setSharingLoadFailed] = useState(false);

    useEffect(() => {
        MessageEndpoint.isEnabled().then(setMessagesEnabled);
    }, []);

    useEffect(() => {
        if (!user?.id) return;

        let active = true;
        RecommendationEndpoint.sharesActivity().then(
            value => {
                if (active) setSavedSharing(value);
            },
            () => {
                if (active) setSharingLoadFailed(true);
            }
        );
        return () => {
            active = false;
        };
    }, [user?.id]);

    useEffect(() => {
        if (configSaved) {
            setTimeout(() => setConfigSaved(false), 2000);
        }
    }, [configSaved])


    async function uploadSelectedAvatar(files: File[]) {
        setUploadingAvatar(true);
        try {
            await uploadAvatar(files[0]);
        } catch (error) {
            toast.danger("Error uploading avatar", {description: error instanceof Error ? error.message : "Please try again"});
        } finally {
            setUploadingAvatar(false);
        }
    }

    async function handleSubmit(values: ProfileFormValues, helpers: FormikHelpers<ProfileFormValues>): Promise<void> {
        if (!formik.dirty) return;

        if (values.sharesActivity !== undefined) {
            try {
                setSavedSharing(await RecommendationEndpoint.setSharesActivity(values.sharesActivity));
                await helpers.setFieldValue("sharesActivity", undefined, false);
            } catch {
                toast.danger("Could not save your sharing preference", {description: "Please try again"});
                return;
            }
        }

        const userUpdate: UserUpdateDto = {};
        if (!user?.managedBySso) {
            if (values.username !== formik.initialValues.username) userUpdate.username = values.username;
            if (values.email !== formik.initialValues.email) userUpdate.email = values.email;
            if (values.newPassword.length > 0) userUpdate.password = values.newPassword;
        }

        if (Object.keys(userUpdate).length > 0) {
            try {
                await UserEndpoint.updateUser(userUpdate);
            } catch {
                toast.danger("Could not update your profile", {description: "Please try again"});
                return;
            }
        }

        helpers.resetForm({
            values: {username: values.username, email: values.email, newPassword: "", passwordRepeat: ""}
        });
        setConfigSaved(true);

        if (userUpdate.password) {
            toast.success("Password changed", {
                description: "Please log in again"
            });
            setTimeout(() => {
                auth.logout();
            }, 500);
        }
    }

    const formik = useFormik<ProfileFormValues>({
        initialValues: {
            username: user?.username,
            email: user?.email,
            newPassword: "",
            passwordRepeat: ""
        },
        onSubmit: handleSubmit,
        validationSchema: user?.managedBySso ? undefined : Yup.object({
            username: Yup.string()
                .required('Required'),
            newPassword: Yup.string()
                .min(8, 'Password must be at least 8 characters long'),
            email: Yup.string()
                .email()
                .required('Required'),
            passwordRepeat: Yup.string()
                .equals([Yup.ref('newPassword')], 'Passwords do not match')
        })
    });
    const saveButtonContent = configSaved ? <CheckIcon/> : "Save";
    const profileFieldsDisabled = user?.managedBySso || formik.isSubmitting;

    return (
        <FormikProvider value={formik}>
            <Form>
                <div className="flex flex-wrap gap-3 grow justify-between mb-8">
                    <h2 className="text-2xl font-bold">My Profile</h2>
                    {auth.state.user?.managedBySso &&
                        <p className="text-warning">Your account is managed externally.</p>}

                    <div className="flex flex-row items-center gap-4">
                        {formik.values.newPassword.length > 0 &&
                            <SmallInfoField icon={InfoIcon}
                                            message="You will be logged out of all current sessions"
                                            className="text-muted"
                            />
                        }
                        <Button
                            variant="primary"
                            isPending={formik.isSubmitting}
                            isDisabled={!formik.dirty || formik.isSubmitting || configSaved}
                            type="submit"
                        >
                            {formik.isSubmitting ? <Spinner size="sm" color="current"/> : saveButtonContent}
                        </Button>
                    </div>
                </div>

                <div className="flex flex-col xl:flex-row flex-1 justify-between gap-6 xl:gap-16">
                    <div className="flex flex-col basis-1/4 mt-8 gap-4">
                        <div className="flex flex-row justify-center">
                            <Avatar className="size-40 m-4 flex flex-row"/>
                        </div>
                        <div className="flex flex-row gap-2 justify-center">
                            <UploadButton label="Upload avatar" accept="image/*"
                                          onFilesSelected={uploadSelectedAvatar}
                                          isPending={uploadingAvatar} isDisabled={auth.state.user?.managedBySso}/>
                            <Tooltip delay={0}>
                                <Tooltip.Trigger>
                                    <Button onPress={removeAvatar} isIconOnly variant="danger"
                                            isDisabled={auth.state.user?.managedBySso}><TrashIcon/></Button>
                                </Tooltip.Trigger>
                                <Tooltip.Content>
                                    <p>Remove your current avatar</p>
                                </Tooltip.Content>
                            </Tooltip>
                        </div>
                    </div>

                    <div className="flex flex-col grow">
                        <Section title="Personal information"/>
                        <Input name="username" label="Username" type="text" autoComplete="username"
                               isDisabled={profileFieldsDisabled}/>
                        <div className="flex flex-col sm:flex-row gap-4">
                            <Input name="email" label="Email" type="email" autoComplete="email"
                                   isDisabled={profileFieldsDisabled || !messagesEnabled}/>
                            {(auth.state.user?.emailConfirmed === false && !auth.state.user.managedBySso) &&
                                <Tooltip delay={0}>
                                    <Tooltip.Trigger>
                                        <Button isIconOnly
                                                onPress={() => {
                                                    EmailConfirmationEndpoint.resendEmailConfirmation().then(
                                                        () => toast.success("Email confirmation message sent", {
                                                            description: "Please check your inbox"
                                                        })
                                                    )
                                                }}
                                                isDisabled={!messagesEnabled}
                                                variant="ghost"
                                                className="size-14"
                                        >
                                            <ArrowCounterClockwiseIcon size={26}/>
                                        </Button>
                                    </Tooltip.Trigger>
                                    <Tooltip.Content>
                                        <p>Resend email confirmation message</p>
                                    </Tooltip.Content>
                                </Tooltip>
                            }
                        </div>
                        {!messagesEnabled &&
                            <div className="flex flex-row gap-2 text-warning -mt-5">
                                <InfoIcon/>
                                <small>
                                    Email services are disabled. Please contact your administrator.
                                </small>
                            </div>
                        }
                        <Section title="Security"/>
                        <Input name="newPassword" label="New Password" type="password"
                               autoComplete="new-password" isDisabled={profileFieldsDisabled}/>
                        <Input name="passwordRepeat" label="Repeat password" type="password"
                               autoComplete="new-password" isDisabled={profileFieldsDisabled}/>
                        {auth.state.user && <>
                            <Section title="Recommendations"/>
                            <RecommendationSharing
                                enabled={formik.values.sharesActivity ?? savedSharing}
                                isSubmitting={formik.isSubmitting}
                                loadFailed={sharingLoadFailed}
                                onChange={enabled => {
                                    void formik.setFieldValue("sharesActivity", enabled === savedSharing ? undefined : enabled, false);
                                }}
                            />
                        </>}
                    </div>
                </div>
            </Form>
        </FormikProvider>
    );
}
