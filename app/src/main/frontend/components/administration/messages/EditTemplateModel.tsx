import React, {useEffect, useState} from "react";
import {
    toast,
    Button,
    Chip,
    Link,
    Modal,
    TextArea
} from "@heroui/react";
import {MessageTemplateEndpoint} from "Frontend/generated/endpoints";
import MessageTemplateDto from "Frontend/generated/org/gameyfin/app/messages/templates/MessageTemplateDto";
import TemplateType from "Frontend/generated/org/gameyfin/app/messages/templates/TemplateType";

interface EditTemplateModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    selectedTemplate: MessageTemplateDto | null;
}

export default function EditTemplateModal({isOpen, onOpenChange, selectedTemplate}: EditTemplateModalProps) {
    const [templateContent, setTemplateContent] = useState<string>("");
    const [defaultPlaceholders, setDefaultPlaceholders] = useState<string[]>([]);

    useEffect(() => {
        if (!isOpen) return;

        MessageTemplateEndpoint.read(selectedTemplate?.key as string, TemplateType.MJML).then((response: any) => {
            setTemplateContent(response as string);
        });

        MessageTemplateEndpoint.getDefaultPlaceholders(TemplateType.MJML).then((response: any) => {
            setDefaultPlaceholders(response as string[]);
        });
    }, [isOpen]);

    async function saveTemplate(template: MessageTemplateDto) {
        await MessageTemplateEndpoint.save(template.key, TemplateType.MJML, templateContent);
    }

    function templateContainsAllRequiredPlaceholders(): boolean {
        if (!selectedTemplate || !selectedTemplate.availablePlaceholders) return false;
        return selectedTemplate.availablePlaceholders
            .every((p) => templateContent.includes(`{${p}}`))
    }

    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
                <Modal.Container size="lg" className="max-w-5xl">
                    <Modal.Dialog>
                        {({close}) => (
                            <>
                                <Modal.CloseTrigger/>
                                <Modal.Header className="flex flex-col gap-1">
                                    <Modal.Heading>Edit {selectedTemplate?.name} Template</Modal.Heading>
                                </Modal.Header>
                                <Modal.Body>
                                    <div className="flex flex-row justify-between items-end">
                                        <table cellPadding="4rem">
                                            <tbody>
                                            <tr>
                                                <td>Required placeholders:</td>
                                                <td>
                                                    <div className="flex flex-row gap-2">
                                                        {selectedTemplate?.availablePlaceholders?.map((placeholder) =>
                                                            <Chip variant="soft"
                                                                  className="rounded-sm"
                                                                  key={placeholder}
                                                                  color={templateContent.includes(`{${placeholder as string}}`) ? "success" : "danger"}
                                                            >{placeholder}</Chip>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>
                                            <tr>
                                                <td>Optional placeholders:</td>
                                                <td>
                                                    <div className="flex flex-row gap-2">
                                                        {defaultPlaceholders.map((placeholder) =>
                                                            <Chip variant="soft"
                                                                  className="rounded-sm"
                                                                  key={placeholder}
                                                                  color={templateContent.includes(`{${placeholder as string}}`) ? "success" : "default"}
                                                            >{placeholder}</Chip>
                                                        )}
                                                    </div>
                                                </td>
                                            </tr>
                                            </tbody>
                                        </table>
                                        <small className="text-right">
                                            Powered by{" "}
                                            <Link href="https://documentation.mjml.io/"
                                                  target="_blank"
                                                  rel="noopener noreferrer"
                                                  className="inline-flex items-center gap-1">
                                                mjml.io
                                                <Link.Icon/>
                                            </Link>
                                        </small>
                                    </div>
                                    <TextArea
                                        autoFocus
                                        value={templateContent}
                                        onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => {
                                            setTemplateContent(e.target.value)
                                        }}
                                        className="resize-y min-h-[500px] w-full rounded-md border border-border bg-surface px-3 py-2"
                                    />
                                </Modal.Body>
                                <Modal.Footer>
                                    <Button variant="danger-soft" onPress={close}>
                                        Cancel
                                    </Button>
                                    <Button variant="primary"
                                            isDisabled={!templateContainsAllRequiredPlaceholders()}
                                            onPress={async () => {
                                                if (selectedTemplate) {
                                                    await saveTemplate(selectedTemplate);
                                                    toast.success("Template saved", {
                                                        description: "Template has been saved"
                                                    });
                                                    close();
                                                }
                                            }}>
                                        Save
                                    </Button>
                                </Modal.Footer>
                            </>
                        )}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}