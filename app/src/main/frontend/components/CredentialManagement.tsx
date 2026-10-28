import React, {useEffect, useState} from "react";
import {
    Button,
    Checkbox,
    CheckboxGroup,
    Description,
    FieldError,
    Label,
    Link,
    Modal,
    Spinner,
    Table
} from "@heroui/react";
import {Form, FormikProvider, useFormik} from "formik";
import * as Yup from "yup";
import CopyButton from "Frontend/components/general/CopyButton";
import Input from "Frontend/components/general/input/Input";
import DatePickerInput from "Frontend/components/general/input/DatePickerInput";
import {CredentialEndpoint} from "Frontend/generated/endpoints";
import CredentialView from "Frontend/generated/org/gameyfin/app/api/security/CredentialView";
import {useAuth} from "Frontend/util/auth";

interface CredentialFormValues {
    name: string;
    expiry: string;
    scopes: string[];
}

const initialValues: CredentialFormValues = {name: "", expiry: "", scopes: []};

function expiryInstant(day: string): Date {
    const date = new Date(`${day}T00:00:00`);
    date.setDate(date.getDate() + 1);
    return date;
}

function expiryDay(value: string): string {
    const date = new Date(new Date(value).getTime() - 1);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

export default function CredentialManagement({service = false}: { service?: boolean }) {
    const auth = useAuth();
    const [credentials, setCredentials] = useState<CredentialView[]>([]);
    const [availableScopes, setAvailableScopes] = useState<string[]>([]);
    const [editing, setEditing] = useState<string>();
    const [secret, setSecret] = useState<string>();
    const [revokeId, setRevokeId] = useState<string>();
    const [confirmation, setConfirmation] = useState<{ action: "delete" | "regenerate"; item: CredentialView }>();
    const [busy, setBusy] = useState(false);
    const [loaded, setLoaded] = useState(false);
    const [error, setError] = useState<string>();
    const allowed = !!auth.state.user && (!service || auth.state.user.roles.some(role => role === "ROLE_ADMIN" || role === "ROLE_SUPERADMIN"));
    const formik = useFormik<CredentialFormValues>({
        initialValues,
        validateOnMount: true,
        validationSchema: Yup.object({
            name: Yup.string().trim().required("Name is required").max(100, "Name must be at most 100 characters"),
            expiry: Yup.string().test("future", "Expiry must be today or a future date", value =>
                !value || expiryInstant(value).getTime() > Date.now()),
            scopes: service ? Yup.array().of(Yup.string()).min(1, "Select at least one permission") : Yup.array()
        }),
        onSubmit: save
    });

    async function refresh() {
        setCredentials(await CredentialEndpoint.list(service));
        setLoaded(true);
    }

    useEffect(() => {
        if (!allowed) return;
        let active = true;
        Promise.all([CredentialEndpoint.list(service), service ? CredentialEndpoint.scopes() : Promise.resolve([])])
            .then(([items, permissions]) => {
                if (active) {
                    setCredentials(items);
                    setAvailableScopes(permissions.sort());
                    setLoaded(true);
                }
            }).catch(() => {
            if (active) setError("Could not load credentials. Please reload this page.");
        });
        return () => {
            active = false;
        };
    }, [service, allowed]);

    function reset() {
        setEditing(undefined);
        formik.resetForm();
    }

    function edit(item: CredentialView) {
        setEditing(item.id);
        const expiry = item.expiresAt ? expiryDay(item.expiresAt) : "";
        formik.resetForm();
        void formik.setValues({name: item.name, scopes: item.scopes, expiry});
        setError(undefined);
    }

    async function save({name, expiry, scopes}: CredentialFormValues) {
        setBusy(true);
        setError(undefined);
        try {
            const input = {
                name: name.trim(),
                expiresAt: expiry ? expiryInstant(expiry).toISOString() : undefined,
                scopes: service ? scopes : []
            };
            if (editing) await CredentialEndpoint.update(editing, service, input);
            else {
                const created = await CredentialEndpoint.create(service, input);
                setSecret(created.secret);
            }
            reset();
            await refresh();
        } catch {
            setError("Could not save. Check the name, future expiry date, and selected permissions.");
        } finally {
            setBusy(false);
        }
    }

    async function revoke(id: string) {
        setBusy(true);
        setError(undefined);
        try {
            await CredentialEndpoint.revoke(id, service);
            if (editing === id) reset();
            setRevokeId(undefined);
            await refresh();
        } catch {
            setError("Could not revoke this credential. Please try again.");
        } finally {
            setBusy(false);
        }
    }

    async function confirmAction() {
        if (!confirmation) return;
        const {action, item} = confirmation;
        setBusy(true);
        setError(undefined);
        try {
            if (action === "delete") await CredentialEndpoint.delete(item.id, service);
            else {
                const regenerated = await CredentialEndpoint.regenerate(item.id, service);
                setSecret(regenerated.secret);
            }
            if (editing === item.id) reset();
            setConfirmation(undefined);
            await refresh();
        } catch {
            setError(`Could not ${action} this credential. Please try again.`);
            setConfirmation(undefined);
        } finally {
            setBusy(false);
        }
    }

    const date = (value?: string) => value ? new Date(value).toLocaleString() : "—";
    const permissionCategories = [...new Set(availableScopes.map(scope => scope.split(":")[0]))];
    const permissionActions = ["read", "write", "delete", ...new Set(availableScopes
        .map(scope => scope.split(":")[1])
        .filter(action => !["read", "write", "delete"].includes(action)))];
    const permissionLabel = (value: string) => value.charAt(0).toUpperCase() + value.slice(1);

    if (!allowed) return <p className="py-4">
        {service ? "Administrator access is required." : "Sign in to manage your personal access tokens."}
    </p>;
    return <FormikProvider value={formik}>
        <section className="space-y-5">
            <h1 className="text-2xl font-bold">{service ? "API keys" : "Personal access tokens"}</h1>
            <p className="text-default-500">{service
                ? "Connect services with selected permissions."
                : "Connect apps using your account's permissions. Tokens stop working when your account is disabled."}
                {" "}
                <Link href="/swagger-ui/index.html" target="_blank" rel="noreferrer">
                    API documentation
                </Link>
            </p>
            {error && <p role="alert" className="text-danger">{error}</p>}
            {secret && <div className="rounded-lg border border-warning p-4 space-y-3" role="status">
                <p className="font-semibold">Copy your new {service ? "API key" : "personal access token"} now. It will
                    not
                    be shown again.</p>
                <div className="flex items-center gap-2 rounded-lg bg-surface-secondary p-3">
                <pre className="m-0 min-w-0 flex-1 overflow-x-auto text-sm font-mono">
                    <code className="select-all">{secret}</code>
                </pre>
                    <CopyButton text={secret} label={service ? "Copy API key" : "Copy personal access token"}/>
                </div>
                <div className="flex gap-2">
                    <Button type="button" variant="secondary" onPress={() => setSecret(undefined)}>I've saved
                        it</Button>
                </div>
            </div>}
            <Form className="rounded-lg border border-default-200 p-4 space-y-4">
                <h2 className="text-lg font-semibold">{editing ? "Edit credential" : `Create ${service ? "API key" : "token"}`}</h2>
                <div className="flex flex-col gap-4 md:flex-row">
                    <Input name="name" label="Name" isRequired placeholder="e.g. Playnite"
                           isDisabled={busy} className="min-w-0 flex-1"/>
                    <DatePickerInput name="expiry" label="Expires at (optional)" granularity="day" isDisabled={busy}
                                     className="w-68 shrink-0"/>
                </div>
                {service && <CheckboxGroup name="scopes" value={formik.values.scopes}
                                           onChange={value => void formik.setFieldValue("scopes", value)}
                                           onBlur={() => void formik.setFieldTouched("scopes", true)}
                                           isRequired isDisabled={busy} validationBehavior="aria"
                                           isInvalid={!!formik.touched.scopes && !!formik.errors.scopes}>
                    <Label>Permissions</Label>
                    <Description className="mb-4">Select each permission needed. Write does not include read or
                        delete.</Description>
                    <Table variant="secondary">
                        <Table.ScrollContainer>
                            <Table.Content aria-label="Credential permissions">
                                <Table.Header>
                                    <Table.Column id="category" isRowHeader>Category</Table.Column>
                                    {permissionActions.map(action =>
                                        <Table.Column key={action}
                                                      id={action}>{permissionLabel(action)}</Table.Column>)}
                                </Table.Header>
                                <Table.Body>
                                    {permissionCategories.map(category => <Table.Row key={category} id={category}>
                                        <Table.Cell>{permissionLabel(category)}</Table.Cell>
                                        {permissionActions.map(action => {
                                            const scope = `${category}:${action}`;
                                            return <Table.Cell key={action}>
                                                {availableScopes.includes(scope) ?
                                                    <Checkbox value={scope} className="mt-0!"
                                                              aria-label={`${permissionLabel(category)}: ${action}`}>
                                                        <Checkbox.Content>
                                                            <Checkbox.Control><Checkbox.Indicator/></Checkbox.Control>
                                                        </Checkbox.Content>
                                                    </Checkbox> :
                                                    <span aria-label="Not available">—</span>
                                                }
                                            </Table.Cell>;
                                        })}
                                    </Table.Row>)}
                                </Table.Body>
                            </Table.Content>
                        </Table.ScrollContainer>
                    </Table>
                    <FieldError className="-ml-1.5 mt-4">{formik.errors.scopes}</FieldError>
                </CheckboxGroup>}
                <div className="flex gap-2">
                    {service && <>
                        <Button type="button" variant="secondary" isDisabled={busy || !loaded}
                                onPress={() => void formik.setFieldValue("scopes", [...availableScopes])}>
                            Select all
                        </Button>
                        <Button type="button" variant="secondary"
                                isDisabled={busy || !formik.values.scopes.length}
                                onPress={() => {
                                    void formik.setFieldTouched("scopes", true, false);
                                    void formik.setFieldValue("scopes", []);
                                }}>
                            Clear selection
                        </Button>
                    </>}
                    <Button type="submit"
                            isPending={formik.isSubmitting}
                            isDisabled={busy || !loaded || !formik.isValid || !!secret && !editing}>
                        {editing ? "Save changes" : "Create"}
                    </Button>
                    {editing &&
                        <Button type="button" variant="secondary" isDisabled={busy} onPress={reset}>Cancel</Button>
                    }
                </div>
            </Form>
            {!loaded ? <Spinner aria-label="Loading credentials"/> : credentials.length === 0 ?
                <p>No {service ? "API keys" : "tokens"} yet.</p> :
                <Table variant="secondary">
                    <Table.ScrollContainer>
                        <Table.Content aria-label={service ? "API keys" : "Personal access tokens"}>
                            <Table.Header>
                                <Table.Column id="name" isRowHeader>Name</Table.Column>
                                <Table.Column id="permissions">Permissions</Table.Column>
                                <Table.Column id="status">Status</Table.Column>
                                <Table.Column id="created">Created</Table.Column>
                                <Table.Column id="expires">Expires</Table.Column>
                                <Table.Column id="last-used">Last used</Table.Column>
                                <Table.Column id="actions">Actions</Table.Column>
                            </Table.Header>
                            <Table.Body>{credentials.map(item => <Table.Row key={item.id} id={item.id}>
                                <Table.Cell>
                                    <span className="font-medium">{item.name}</span>{" "}
                                    <code className="whitespace-nowrap">({item.prefix}…)</code>
                                </Table.Cell>
                                <Table.Cell>
                                    <div className="text-default-500 max-w-sm wrap-break-word">
                                        {service ? item.scopes.join(", ") : "Current account permissions"}
                                    </div>
                                </Table.Cell>
                                <Table.Cell><p className="capitalize">{item.status.toLowerCase()}</p></Table.Cell>
                                <Table.Cell>{date(item.createdAt)}</Table.Cell>
                                <Table.Cell>{item.expiresAt ? new Date(new Date(item.expiresAt).getTime() - 1).toLocaleDateString() : "—"}</Table.Cell>
                                <Table.Cell>{date(item.lastUsedAt)}</Table.Cell>
                                <Table.Cell>{item.revokedAt ?
                                    <Button type="button" size="sm" variant="danger" isDisabled={busy}
                                            onPress={() => setConfirmation({action: "delete", item})}>Delete</Button> :
                                    <div className="flex gap-2">
                                        <Button type="button" size="sm" variant="secondary" isDisabled={busy}
                                                onPress={() => edit(item)}>Edit</Button>
                                        <Button type="button" size="sm" variant="secondary"
                                                isDisabled={busy || !!secret}
                                                onPress={() => setConfirmation({
                                                    action: "regenerate",
                                                    item
                                                })}>Regenerate</Button>
                                        {revokeId === item.id ? <>
                                                <Button type="button" size="sm" variant="danger"
                                                        isDisabled={busy}
                                                        onPress={() => revoke(item.id)}>Confirm
                                                    revoke</Button>
                                                <Button type="button" size="sm"
                                                        onPress={() => setRevokeId(undefined)}>Cancel
                                                </Button>
                                            </>
                                            : <Button type="button" size="sm" variant="danger" isDisabled={busy}
                                                      onPress={() => setRevokeId(item.id)}>Revoke</Button>}
                                    </div>}</Table.Cell>
                            </Table.Row>)}</Table.Body>
                        </Table.Content>
                    </Table.ScrollContainer>
                </Table>}
            <Modal>
                <Modal.Backdrop isOpen={!!confirmation} onOpenChange={open => {
                    if (!open && !busy) setConfirmation(undefined);
                }}
                                isDismissable={!busy} isKeyboardDismissDisabled={busy} variant="opaque">
                    <Modal.Container>
                        <Modal.Dialog>
                            <Modal.Header>
                                <Modal.Heading>{confirmation?.action === "delete" ? "Delete credential?" : "Regenerate credential?"}</Modal.Heading>
                            </Modal.Header>
                            <Modal.Body>
                                <p>{confirmation?.action === "delete"
                                    ? `Permanently delete the revoked credential “${confirmation.item.name}”? This cannot be undone.`
                                    : `Generate a new secret for “${confirmation?.item.name}”? The previous secret will stop working immediately. Name, permissions and expiry remain unchanged.`}</p>
                            </Modal.Body>
                            <Modal.Footer>
                                <Button type="button" variant="secondary" isDisabled={busy}
                                        onPress={() => setConfirmation(undefined)}>Cancel</Button>
                                <Button type="button" variant="danger" isPending={busy} isDisabled={busy}
                                        onPress={confirmAction}>
                                    {confirmation?.action === "delete" ? "Delete permanently" : "Regenerate"}
                                </Button>
                            </Modal.Footer>
                        </Modal.Dialog>
                    </Modal.Container>
                </Modal.Backdrop>
            </Modal>
        </section>
    </FormikProvider>;
}
