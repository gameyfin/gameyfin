import {useEffect, useState} from "react";
import {Button} from "@heroui/react";
import ConfirmModal from "Frontend/components/general/modals/ConfirmModal";
import {UserEndpoint} from "Frontend/generated/endpoints";
import {useAuth} from "Frontend/util/auth";

export default function AccountManagement() {
    const auth = useAuth();
    const [isOpen, setIsOpen] = useState(false);
    const [isPending, setIsPending] = useState(false);
    const [isDeleted, setIsDeleted] = useState(false);
    const [error, setError] = useState<string>();

    const [canDelete, setCanDelete] = useState(false);
    const [checkingDeletion, setCheckingDeletion] = useState(true);

    useEffect(() => {
        const user = auth.state.user;
        if (!user) return;
        if (!user.roles.includes("ROLE_SUPERADMIN")) {
            setCanDelete(true);
            setCheckingDeletion(false);
            return;
        }
        UserEndpoint.getAllUsers().then(users => {
            setCanDelete(users.some(other => other.id !== user.id && other.roles.includes("ROLE_SUPERADMIN")));
        }).catch(() => setError("Could not check whether your account can be deleted. Please reload and try again."))
            .finally(() => setCheckingDeletion(false));
    }, [auth.state.user]);

    async function signOut() {
        setIsPending(true);
        setError(undefined);
        try {
            await auth.logout();
        } catch {
            setError("Your account was deleted, but signing out failed. Please try signing out again.");
        } finally {
            setIsPending(false);
        }
    }

    async function deleteAccount() {
        if (isPending || isDeleted || !canDelete) return;
        setIsPending(true);
        setError(undefined);
        try {
            await UserEndpoint.deleteUser();
        } catch (error) {
            setError(error instanceof Error ? error.message : "Could not delete your account. Please try again.");
            setIsPending(false);
            return;
        }
        setIsDeleted(true);
        setIsOpen(false);
        await signOut();
    }

    if (!auth.state.user) return <p className="py-4">Sign in to manage your account.</p>;

    return (
        <section className="space-y-5">
            <h1 className="text-2xl font-bold">Manage account</h1>
            {isDeleted ? (
                <div className="space-y-4">
                    <output>Your account has been deleted.</output>
                    {error && <p role="alert" className="text-danger">{error}</p>}
                    <Button onPress={signOut} isPending={isPending}>Sign out</Button>
                </div>
            ) : (
                <div className="rounded-lg border border-danger p-4 space-y-4">
                    <h2 className="text-lg font-semibold">Delete account</h2>
                    <p>Permanently delete your account. This action cannot be undone.</p>
                    {!isOpen && error && <p role="alert" className="text-danger">{error}</p>}
                    <div className="flex flex-row items-center gap-2">
                        <Button variant="danger" isDisabled={checkingDeletion || !canDelete} onPress={() => {
                            setError(undefined);
                            setIsOpen(true);
                        }}>
                            Delete my account
                        </Button>
                        {!checkingDeletion && !canDelete && !error &&
                            <p className="text-danger">The last Superadmin account cannot be deleted. Another Superadmin
                                must remain.</p>}
                    </div>
                </div>
            )}
            <ConfirmModal
                isOpen={isOpen}
                onOpenChange={setIsOpen}
                title="Delete your account?"
                confirmLabel="Delete my account"
                isPending={isPending}
                onConfirm={deleteAccount}
            >
                <p>Are you sure you want to permanently delete your account, <strong>{auth.state.user.username}</strong>?
                </p>
                <p>This action cannot be undone. You will be signed out after your account is deleted.</p>
                {error && <p role="alert" className="text-danger">{error}</p>}
            </ConfirmModal>
        </section>
    );
}
