import {Button, Label, Modal, Spinner, Surface, Switch, toast, useOverlayState} from "@heroui/react";
import {SparkleIcon} from "@phosphor-icons/react";
import {useEffect, useState} from "react";
import type {RuleGroupType} from "react-querybuilder";
import ResponsiveDialog from "Frontend/components/layout/ResponsiveDialog";
import SmartCollectionRulesEditor, {ruleError, toRuleGroup, toSmartRule} from "./SmartCollectionRulesEditor";
import {CollectionEndpoint} from "Frontend/generated/endpoints";
import type SmartCollectionEditor from "Frontend/generated/org/gameyfin/app/collections/dto/SmartCollectionEditor";
import type SmartCollectionPreview from "Frontend/generated/org/gameyfin/app/collections/dto/SmartCollectionPreview";

export default function SmartCollectionSection({collectionId, enabled}: { collectionId: number; enabled: boolean }) {
    const modal = useOverlayState();

    return <Surface className="flex flex-col items-start gap-3 rounded-xl p-4 sm:p-6">
        <h2 className="flex items-center gap-2 text-lg font-semibold">
            <SparkleIcon weight={enabled ? "fill" : "regular"}
                         className={enabled ? "text-accent" : "text-muted"} aria-hidden="true"/>
            Smart Collection
        </h2>
        <p className="text-sm text-muted">
            {enabled ? "Collection is updated automatically." : "Set rules to update this collection automatically."}
        </p>
        <Button type="button" variant="secondary" onPress={modal.open}>
            {enabled ? "Manage rules" : "Set up rules"}
        </Button>
        {modal.isOpen && <SmartCollectionDialog collectionId={collectionId} onClose={modal.close}/>}
    </Surface>;
}

function SmartCollectionDialog({collectionId, onClose}: { collectionId: number; onClose: () => void }) {
    const [editor, setEditor] = useState<SmartCollectionEditor>();
    const [enabled, setEnabled] = useState(false);
    const [query, setQuery] = useState<RuleGroupType>(() => toRuleGroup());
    const [preview, setPreview] = useState<SmartCollectionPreview>();
    const [pending, setPending] = useState<"preview" | "save">();
    const [error, setError] = useState<string>();
    const rule = toSmartRule(query);
    const validation = ruleError(rule);

    useEffect(() => {
        let active = true;
        void CollectionEndpoint.getSmartEditor(collectionId).then(result => {
            if (!active) return;
            setQuery(toRuleGroup(result.settings.rule));
            setEnabled(result.settings.enabled || !result.settings.rule);
            setEditor(result);
        }).catch(() => {
            if (active) setError("Could not load collection rules.");
        });
        return () => {
            active = false;
        };
    }, [collectionId]);

    function clearPreview() {
        setPreview(undefined);
        setError(undefined);
    }

    async function previewRules() {
        if (validation) return;
        setPending("preview");
        clearPreview();
        try {
            setPreview(await CollectionEndpoint.previewSmartCollection(collectionId, rule));
        } catch {
            setError("Could not preview the rules.");
        } finally {
            setPending(undefined);
        }
    }

    async function save() {
        setPending("save");
        setError(undefined);
        try {
            await CollectionEndpoint.saveSmartSettings(collectionId, {enabled, rule: enabled ? rule : undefined});
            toast.success(enabled ? "Smart Collection saved" : "Smart Collection disabled");
            onClose();
        } catch {
            setError("Could not save Smart Collection settings.");
            setPending(undefined);
        }
    }

    return <Modal>
        <Modal.Backdrop isOpen onOpenChange={open => !open && !pending && onClose()} isDismissable={!pending}>
            <ResponsiveDialog size="lg" scroll="inside" className="max-w-4xl">
                <Modal.Dialog>
                    <Modal.CloseTrigger isDisabled={!!pending}/>
                    <Modal.Header><Modal.Heading>Smart Collection rules</Modal.Heading></Modal.Header>
                    <Modal.Body className="flex flex-col gap-4">
                        {!editor && !error && <Spinner aria-label="Loading collection rules"/>}
                        {editor && <>
                            <Switch isSelected={enabled} isDisabled={!!pending} onChange={value => {
                                setEnabled(value);
                                clearPreview();
                            }}>
                                <Switch.Content>
                                    <Switch.Control><Switch.Thumb/></Switch.Control>
                                    <Label>Enable Smart Collection</Label>
                                </Switch.Content>
                            </Switch>
                            {enabled ? <>
                                <SmartCollectionRulesEditor query={query} disabled={!!pending} editor={editor}
                                                            onChange={next => {
                                                                setQuery(next);
                                                                clearPreview();
                                                            }}/>
                                {!preview && <p className="text-sm text-muted">
                                    {validation ?? "Preview how these rules will change membership before saving."}
                                </p>}
                            </> : <p className="text-sm text-muted">
                                The collection keeps its current games. Saved rules are available when you enable it
                                again.
                            </p>}
                        </>}
                    </Modal.Body>
                    <Modal.Footer className="flex flex-wrap items-center gap-2">
                        {error && <p className="w-full text-sm text-danger" role="alert">{error}</p>}
                        <p className="w-full text-sm text-muted tabular-nums empty:sr-only sm:mr-auto sm:w-auto"
                           aria-live="polite">
                            {preview && `${preview.matching.toLocaleString()} matching · ${preview.added.toLocaleString()} to add · ${preview.removed.toLocaleString()} to remove`}
                        </p>
                        <Button type="button" variant="tertiary" onPress={onClose}
                                isDisabled={!!pending}>Cancel</Button>
                        {editor && enabled &&
                            <Button type="button" variant="secondary" onPress={previewRules}
                                    isPending={pending === "preview"} isDisabled={!!pending || !!validation}>
                                Preview
                            </Button>}
                        {editor && <Button type="button" variant="primary" onPress={save}
                                           isPending={pending === "save"}
                                           isDisabled={!!pending || (enabled ? !preview : !editor.settings.enabled)}>
                            {enabled ? "Save rules" : "Disable"}
                        </Button>}
                    </Modal.Footer>
                </Modal.Dialog>
            </ResponsiveDialog>
        </Modal.Backdrop>
    </Modal>;
}
