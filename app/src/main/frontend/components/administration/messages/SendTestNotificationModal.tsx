import React from "react";
import {Form, Formik} from "formik";
import {toast, Button, Modal} from "@heroui/react";
import Input from "Frontend/components/general/input/Input";
import {MessageEndpoint} from "Frontend/generated/endpoints";
import * as Yup from "yup";
import MessageTemplateDto from "Frontend/generated/org/gameyfin/app/messages/templates/MessageTemplateDto";

interface SendTestNotificationModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    selectedTemplate: MessageTemplateDto;
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
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
                <Modal.Container size="lg" className="max-w-3xl">
                    <Modal.Dialog>
                        {({close}) => (
                            <>
                                <Modal.CloseTrigger/>
                                <Formik
                                    initialValues={{}}
                                    isInitialValid={false}
                                    onSubmit={async (values) => {
                                        await MessageEndpoint.sendTestNotification(selectedTemplate.key, values);
                                        toast.success("Notification sent", {
                                            description: "Test notification to you has been sent"
                                        });
                                        close();
                                    }}
                                    validationSchema={generateValidationSchema(selectedTemplate.availablePlaceholders)}
                                >
                                    {(formik) => (
                                        <Form>
                                            <Modal.Header className="flex flex-col gap-1">
                                                <Modal.Heading>
                                                    Send {selectedTemplate?.name} Test Message
                                                </Modal.Heading>
                                            </Modal.Header>
                                            <Modal.Body>
                                                <p className="text-ls font-semibold mb-4">Fill the placeholders of
                                                    the template</p>
                                                {selectedTemplate.availablePlaceholders.map((placeholder) =>
                                                    <Input key={placeholder} label={placeholder} name={placeholder}/>
                                                )}
                                            </Modal.Body>
                                            <Modal.Footer>
                                                <Button variant="danger-soft" onPress={close}>
                                                    Close
                                                </Button>
                                                <Button variant="primary" type="submit" isDisabled={!formik.isValid}>
                                                    Send
                                                </Button>
                                            </Modal.Footer>
                                        </Form>
                                    )}
                                </Formik>
                            </>
                        )}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}