import React, {useState} from "react";
import {
    Alert,
    Button,
    Chip,
    FieldError,
    Input,
    Label,
    Modal,
    Spinner,
    Switch,
    TextField,
    toast,
    Tooltip,
    useOverlayState
} from "@heroui/react";
import {SealCheckIcon, TrashIcon, WarningCircleIcon} from "@phosphor-icons/react";
import {useSnapshot} from "valtio/react";
import moment from "moment-timezone";
import PluginMarketplaceDto from "Frontend/generated/org/gameyfin/app/core/plugins/marketplace/dto/PluginMarketplaceDto";
import {
    addMarketplace,
    marketplaceState,
    removeMarketplace,
    updateMarketplace
} from "Frontend/state/MarketplaceState";
import ConfirmModal from "Frontend/components/general/modals/ConfirmModal";

interface PluginMarketplacesModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
}

export default function PluginMarketplacesModal({isOpen, onOpenChange}: PluginMarketplacesModalProps) {
    const state = useSnapshot(marketplaceState);

    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
                <Modal.Container size="lg" scroll="inside">
                    <Modal.Dialog>
                        {({close}) => (
                            <>
                                <Modal.Header>
                                    <Modal.Heading>Plugin marketplaces</Modal.Heading>
                                </Modal.Header>
                                <Modal.Body className="flex flex-col gap-4 text-sm">
                                    <p className="text-muted">
                                        Marketplaces are git repositories which provide an index of plugins.
                                        Gameyfin checks enabled marketplaces for new plugins and updates.
                                    </p>
                                    <div className="flex flex-col gap-2">
                                        {state.marketplaces.map(marketplace =>
                                            <MarketplaceRow key={marketplace.id}
                                                            marketplace={marketplace as PluginMarketplaceDto}/>
                                        )}
                                    </div>
                                    <AddMarketplaceForm/>
                                </Modal.Body>
                                <Modal.Footer>
                                    <Button variant="ghost" onPress={close}>Close</Button>
                                </Modal.Footer>
                            </>
                        )}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}

function MarketplaceRow({marketplace}: { marketplace: PluginMarketplaceDto }) {
    const removeModal = useOverlayState();
    const [isUpdating, setIsUpdating] = useState(false);
    const [isRemoving, setIsRemoving] = useState(false);

    async function toggleEnabled(enabled: boolean) {
        setIsUpdating(true);
        try {
            await updateMarketplace(marketplace.id, {enabled});
        } catch (e: any) {
            toast.danger("Failed to update marketplace", {description: e?.message});
        } finally {
            setIsUpdating(false);
        }
    }

    async function remove() {
        setIsRemoving(true);
        try {
            await removeMarketplace(marketplace.id);
            removeModal.close();
            toast.success("Marketplace removed", {description: `"${marketplace.name}" has been removed`});
        } catch (e: any) {
            toast.danger("Failed to remove marketplace", {description: e?.message});
        } finally {
            setIsRemoving(false);
        }
    }

    return (
        <div className="flex flex-row items-center gap-3 rounded-xl bg-surface-secondary p-3">
            <div className="flex flex-col grow min-w-0">
                <div className="flex flex-row items-center gap-1 font-semibold">
                    {marketplace.name}
                    {marketplace.official &&
                        <Tooltip delay={0}>
                            <Tooltip.Trigger className="flex">
                                <span className="inline-flex"><SealCheckIcon className="fill-success" weight="fill"/></span>
                            </Tooltip.Trigger>
                            <Tooltip.Content placement="bottom" className="bg-foreground text-background">
                                Official Gameyfin marketplace
                            </Tooltip.Content>
                        </Tooltip>
                    }
                    {marketplace.lastError &&
                        <Tooltip delay={0}>
                            <Tooltip.Trigger className="flex">
                                <span className="inline-flex"><WarningCircleIcon className="fill-danger" weight="fill"/></span>
                            </Tooltip.Trigger>
                            <Tooltip.Content placement="bottom" className="bg-foreground text-background max-w-md">
                                {marketplace.lastError}
                            </Tooltip.Content>
                        </Tooltip>
                    }
                </div>
                <span className="text-xs text-muted truncate" title={marketplace.url}>{marketplace.url}</span>
                {marketplace.enabled &&
                    <span className="text-xs text-muted">
                        {marketplace.pluginCount} plugin(s)
                        {marketplace.lastFetchedAt && ` · updated ${moment(marketplace.lastFetchedAt).fromNow()}`}
                    </span>
                }
            </div>
            {isUpdating && <Spinner size="sm"/>}
            {marketplace.enabled && marketplace.lastError && !marketplace.lastFetchedAt &&
                <Chip size="sm" variant="soft" color="danger">Unreachable</Chip>}
            <Switch isSelected={marketplace.enabled}
                    isDisabled={isUpdating}
                    onChange={toggleEnabled}
                    aria-label={`${marketplace.enabled ? "Disable" : "Enable"} marketplace ${marketplace.name}`}>
                <Switch.Content>
                    <Switch.Control>
                        <Switch.Thumb/>
                    </Switch.Control>
                </Switch.Content>
            </Switch>
            {!marketplace.official &&
                <Button isIconOnly variant="ghost" size="sm" aria-label={`Remove marketplace ${marketplace.name}`}
                        onPress={removeModal.open}>
                    <TrashIcon/>
                </Button>
            }
            <ConfirmModal isOpen={removeModal.isOpen}
                          onOpenChange={removeModal.setOpen}
                          title="Remove marketplace"
                          confirmLabel="Remove"
                          isPending={isRemoving}
                          onConfirm={remove}>
                <p>Do you really want to remove the marketplace <b>{marketplace.name}</b>?</p>
                <p className="text-muted">
                    Plugins installed from this marketplace stay installed, but won't receive updates anymore.
                </p>
            </ConfirmModal>
        </div>
    );
}

function AddMarketplaceForm() {
    const [name, setName] = useState("");
    const [url, setUrl] = useState("");
    const [error, setError] = useState<string>();
    const [isAdding, setIsAdding] = useState(false);

    async function add() {
        setIsAdding(true);
        setError(undefined);
        try {
            const added = await addMarketplace({name: name.trim(), url: url.trim()});
            toast.success("Marketplace added", {
                description: `"${added.name}" provides ${added.pluginCount} plugin(s)`
            });
            setName("");
            setUrl("");
        } catch (e: any) {
            setError(e?.message ?? "Failed to add marketplace");
        } finally {
            setIsAdding(false);
        }
    }

    return (
        <form className="flex flex-col gap-3"
              onSubmit={(e) => {
                  e.preventDefault();
                  void add();
              }}>
            <h4 className="font-bold">Add marketplace</h4>
            <Alert status="warning">
                <Alert.Indicator/>
                <Alert.Content>
                    <Alert.Description>
                        Third-party marketplaces are not reviewed by the Gameyfin team.
                        Plugins have full access to your Gameyfin instance, so only add marketplaces you trust.
                    </Alert.Description>
                </Alert.Content>
            </Alert>
            <div className="flex flex-col md:flex-row gap-3">
                <TextField className="md:w-1/3" value={name} onChange={setName} isRequired isDisabled={isAdding}>
                    <Label>Name</Label>
                    <Input placeholder="My marketplace"/>
                </TextField>
                <TextField className="grow" value={url} onChange={setUrl} isRequired isDisabled={isAdding}
                           isInvalid={!!error}>
                    <Label>Repository URL</Label>
                    <Input placeholder="https://github.com/owner/repository"/>
                    <FieldError>{error}</FieldError>
                </TextField>
            </div>
            <div className="flex flex-row justify-end">
                <Button type="submit" variant="primary" isPending={isAdding}
                        isDisabled={!name.trim() || !url.trim()}>
                    {isAdding && <Spinner size="sm" color="current"/>}
                    Add marketplace
                </Button>
            </div>
        </form>
    );
}
