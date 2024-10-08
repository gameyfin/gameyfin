import React from "react";
import {Form, Formik} from "formik";
import {Button, Modal, ModalBody, ModalContent, ModalFooter, ModalHeader} from "@nextui-org/react";
import {toast} from "sonner";
import Input from "Frontend/components/general/Input";
import {MessageEndpoint} from "Frontend/generated/endpoints";
import * as Yup from "yup";
import MessageTemplateDto from "Frontend/generated/de/grimsi/gameyfin/messages/templates/MessageTemplateDto";

interface SendTestNotificationModalProps {
    isOpen: boolean;
    onOpenChange: () => void;
    selectedTemplate: MessageTemplateDto | null;
}

export default function SendTestNotificationModal({
                                                      isOpen,
                                                      onOpenChange,
                                                      selectedTemplate
                                                  }: SendTestNotificationModalProps) {

    function generateValidationSchema(placeholders: string[]) {
        const shape: { [key: string]: Yup.StringSchema } = {};
        placeholders.forEach(placeholder => {
            shape[placeholder] = Yup.string().required(`Placeholder ${placeholder} is required`);
        });
        return Yup.object().shape(shape);
    }

    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} size="3xl">
            <ModalContent>
                {(onClose) => (
                    <>
                        <Formik
                            initialValues={{}}
                            isInitialValid={false}
                            onSubmit={async (values) => {
                                await MessageEndpoint.sendTestNotification(selectedTemplate?.key, values);
                                toast.success("Test notification to you has been sent");
                                onClose();
                            }}
                            validationSchema={generateValidationSchema(selectedTemplate?.availablePlaceholders as string[])}
                        >
                            {(formik) => (
                                <Form>
                                    <ModalHeader className="flex flex-col gap-1">
                                        Send {selectedTemplate?.name} Test Message
                                    </ModalHeader>
                                    <ModalBody>
                                        <p className="text-ls font-semibold mb-4">Fill the placeholders of the
                                            template</p>
                                        {selectedTemplate?.availablePlaceholders?.map((placeholder) =>
                                            <Input key={placeholder} label={placeholder} name={placeholder}/>
                                        )}
                                    </ModalBody>
                                    <ModalFooter>
                                        <Button color="danger" variant="light" onPress={onClose}>
                                            Close
                                        </Button>
                                        <Button color="primary" type="submit" isDisabled={!formik.isValid}>
                                            Send
                                        </Button>
                                    </ModalFooter>
                                </Form>
                            )}
                        </Formik>
                    </>
                )}
            </ModalContent>
        </Modal>
    );
}