import React, {useEffect, useState} from "react";
import {
    Button,
    Chip,
    Link,
    Modal,
    ModalBody,
    ModalContent,
    ModalFooter,
    ModalHeader,
    Textarea
} from "@nextui-org/react";
import {toast} from "sonner";
import {MessageTemplateEndpoint} from "Frontend/generated/endpoints";
import MessageTemplateDto from "Frontend/generated/de/grimsi/gameyfin/notifications/templates/MessageTemplateDto";
import TemplateType from "Frontend/generated/de/grimsi/gameyfin/notifications/templates/TemplateType";

interface EditTemplateModalProps {
    isOpen: boolean;
    onOpenChange: () => void;
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

    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} size="5xl">
            <ModalContent>
                {(onClose) => (
                    <>
                        <ModalHeader
                            className="flex flex-col gap-1">Edit {selectedTemplate?.name} Template</ModalHeader>
                        <ModalBody>
                            <div className="flex flex-row justify-between items-end">
                                <table cellPadding="4rem">
                                    <tbody>
                                    <tr>
                                        <td>Required placeholders:</td>
                                        <td>
                                            <div className="flex flex-row gap-2">
                                                {selectedTemplate?.availablePlaceholders?.map((placeholder) =>
                                                    <Chip radius="sm"
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
                                                    <Chip radius="sm"
                                                          key={placeholder}
                                                          color={templateContent.includes(`{${placeholder as string}}`) ? "success" : "default"}
                                                    >{placeholder}</Chip>
                                                )}
                                            </div>
                                        </td>
                                    </tr>
                                    </tbody>
                                </table>
                                <small className="text-right">Powered by <Link href="https://documentation.mjml.io/"
                                                                               target="_blank">mjml.io</Link></small>
                            </div>
                            <Textarea
                                size="lg"
                                autoFocus
                                disableAutosize
                                value={templateContent}
                                onChange={(e) => {
                                    setTemplateContent(e.target.value)
                                }}
                                classNames={{
                                    input: "resize-y min-h-[500px]"
                                }}
                            />
                        </ModalBody>
                        <ModalFooter>
                            <Button color="danger" variant="light" onPress={onClose}>
                                Close
                            </Button>
                            <Button color="primary" onPress={async () => {
                                if (selectedTemplate) {
                                    await saveTemplate(selectedTemplate);
                                    toast.success("Template saved");
                                    onClose();
                                }
                            }}>
                                Save
                            </Button>
                        </ModalFooter>
                    </>
                )}
            </ModalContent>
        </Modal>
    );
}