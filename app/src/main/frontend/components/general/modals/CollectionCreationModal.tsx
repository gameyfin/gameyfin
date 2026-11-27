import {Button, Label, Modal, Spinner, Switch, toast} from "@heroui/react";
import {Form, Formik} from "formik";
import {useEffect, useState} from "react";
import * as Yup from "yup";
import CollectionVisibilitySelect from "Frontend/components/general/collections/CollectionVisibilitySelect";
import SmartCollectionRulesEditor, {
    ruleError,
    toRuleGroup,
    toSmartRule
} from "Frontend/components/general/collections/SmartCollectionRulesEditor";
import Input from "Frontend/components/general/input/Input";
import TextAreaInput from "Frontend/components/general/input/TextAreaInput";
import ResponsiveDialog from "Frontend/components/layout/ResponsiveDialog";
import {CollectionEndpoint} from "Frontend/generated/endpoints";
import type SmartCollectionEditor from "Frontend/generated/org/gameyfin/app/collections/dto/SmartCollectionEditor";
import CollectionVisibility from "Frontend/generated/org/gameyfin/app/collections/entities/CollectionVisibility";

interface CollectionCreationModalProps {
    owned?: boolean;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
}

export default function CollectionCreationModal({owned = false, isOpen, onOpenChange}: CollectionCreationModalProps) {
    return <Modal>
        <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
            <ResponsiveDialog size="lg" scroll="inside" className="max-w-4xl">
                <Modal.Dialog>
                    {({close}) => isOpen && <CollectionCreationForm owned={owned} close={close}/>}
                </Modal.Dialog>
            </ResponsiveDialog>
        </Modal.Backdrop>
    </Modal>;
}

function CollectionCreationForm({owned, close}: { owned: boolean; close: () => void }) {
    const [smart, setSmart] = useState(false);
    const [query, setQuery] = useState(() => toRuleGroup());
    const [editor, setEditor] = useState<SmartCollectionEditor>();
    const [editorError, setEditorError] = useState(false);
    const [submitError, setSubmitError] = useState(false);
    const rule = smart ? toSmartRule(query) : undefined;
    const validation = rule && ruleError(rule);
    const canCreate = !smart || (!!editor && !validation);

    useEffect(() => {
        if (!smart || editor) return;
        let active = true;
        setEditorError(false);
        void CollectionEndpoint.getSmartEditor(undefined).then(result => {
            if (active) setEditor(result);
        }).catch(() => {
            if (active) setEditorError(true);
        });
        return () => {
            active = false;
        };
    }, [smart, editor]);

    return <Formik
        initialValues={{
            name: "",
            description: "",
            visibility: owned ? CollectionVisibility.PRIVATE : CollectionVisibility.PUBLIC
        }}
        validationSchema={Yup.object({
            name: Yup.string()
                .trim()
                .required("Collection name is required")
                .max(255, "Collection name must be 255 characters or less")
        })}
        onSubmit={async values => {
            if (!canCreate) return;
            setSubmitError(false);
            const collection = {...values, name: values.name.trim(), smartRule: rule};
            try {
                if (owned) await CollectionEndpoint.createCustomCollection(collection);
                else await CollectionEndpoint.createCollection(collection);
                toast.success("New collection created", {
                    description: `Collection ${collection.name} created!`
                });
                close();
            } catch {
                setSubmitError(true);
            }
        }}
    >
        {formik => <Form className="flex min-h-0 flex-col">
            <Modal.Header>
                <Modal.Heading>Create a new collection</Modal.Heading>
            </Modal.Header>
            <Modal.Body className="flex flex-col gap-3">
                <Input name="name" label="Collection Name" placeholder="Enter collection name" isRequired/>
                <TextAreaInput name="description" label="Collection Description"
                               placeholder="Enter collection description"/>
                <CollectionVisibilitySelect value={formik.values.visibility}
                                            onChange={value => formik.setFieldValue("visibility", value)}/>
                <Switch isSelected={smart} isDisabled={formik.isSubmitting} onChange={setSmart}>
                    <Switch.Content>
                        <Switch.Control><Switch.Thumb/></Switch.Control>
                        <Label>Smart Collection</Label>
                    </Switch.Content>
                </Switch>
                {smart && (editor ? <>
                    <SmartCollectionRulesEditor query={query} onChange={setQuery} editor={editor}
                                                disabled={formik.isSubmitting}/>
                    {validation && <p className="text-sm text-muted">{validation}</p>}
                </> : editorError ? <p className="text-sm text-danger" role="alert">Could not load rule options.</p>
                    : <Spinner aria-label="Loading rule options"/>)}
                {submitError && <p className="text-sm text-danger" role="alert">Could not create the collection.</p>}
            </Modal.Body>
            <Modal.Footer className="flex flex-row justify-end">
                <Button variant="ghost" onPress={close} isDisabled={formik.isSubmitting}>Cancel</Button>
                <Button variant="primary" type="submit" isPending={formik.isSubmitting}
                        isDisabled={formik.isSubmitting || !formik.values.name.trim() || !formik.isValid || !canCreate}>
                    {formik.isSubmitting ? <Spinner size="sm" color="current"/> : "Add"}
                </Button>
            </Modal.Footer>
        </Form>}
    </Formik>;
}
