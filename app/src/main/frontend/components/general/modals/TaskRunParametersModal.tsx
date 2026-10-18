import React from "react";
import {Button, Modal, Spinner} from "@heroui/react";
import {Form, Formik, FormikErrors, getIn, setIn} from "formik";
import type TaskDto from "Frontend/generated/org/gameyfin/app/core/tasks/dto/TaskDto";
import TaskConfigFormField, {
    fromTaskFieldValue,
    toTaskFieldValue,
    validateTaskFieldValue
} from "Frontend/components/general/tasks/TaskConfigFormField";

interface TaskRunParametersModalProps {
    task: TaskDto;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    onRun: (task: TaskDto, parameters: Record<string, string | undefined>) => Promise<void>;
}

interface RunFormValues {
    parameters: Record<string, unknown>;
}

function parameterFieldName(key: string) {
    return `parameters.${key}`;
}

/**
 * Asks for the parameters of a single (manual) task run.
 */
export default function TaskRunParametersModal({task, isOpen, onOpenChange, onRun}: TaskRunParametersModalProps) {

    function initialValues(): RunFormValues {
        let values: RunFormValues = {parameters: {}};
        for (const meta of task.parametersMetadata) {
            values = setIn(values, parameterFieldName(meta.key), toTaskFieldValue(meta, meta.default));
        }
        return values;
    }

    function validate(values: RunFormValues): FormikErrors<RunFormValues> {
        let errors: FormikErrors<RunFormValues> = {};
        for (const meta of task.parametersMetadata) {
            const error = validateTaskFieldValue(meta, getIn(values, parameterFieldName(meta.key)));
            if (error) errors = setIn(errors, parameterFieldName(meta.key), error);
        }
        return errors;
    }

    async function run(values: RunFormValues, close: () => void) {
        const parameters: Record<string, string | undefined> = {};
        for (const meta of task.parametersMetadata) {
            parameters[meta.key] = fromTaskFieldValue(meta, getIn(values, parameterFieldName(meta.key)));
        }
        await onRun(task, parameters);
        close();
    }

    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
                <Modal.Container size="lg">
                    <Modal.Dialog>
                        {({close}) => (
                            <Formik initialValues={initialValues()}
                                    validate={validate}
                                    enableReinitialize={true}
                                    onSubmit={(values) => run(values, close)}>
                                {(formik) => (
                                    <Form>
                                        <Modal.Header>
                                            <Modal.Heading>Run {task.name}</Modal.Heading>
                                        </Modal.Header>
                                        <Modal.Body className="flex flex-col gap-2">
                                            {task.description && <p className="text-sm text-muted">{task.description}</p>}
                                            <h4 className="text-l font-bold mt-2">Parameters</h4>
                                            {task.parametersMetadata.map(meta => (
                                                <TaskConfigFormField key={meta.key}
                                                                     meta={meta}
                                                                     name={parameterFieldName(meta.key)}/>
                                            ))}
                                        </Modal.Body>
                                        <Modal.Footer>
                                            <Button variant="ghost" onPress={close}>Cancel</Button>
                                            <Button variant="primary"
                                                    type="submit"
                                                    isPending={formik.isSubmitting}
                                                    isDisabled={formik.isSubmitting}>
                                                {formik.isSubmitting ? <Spinner size="sm" color="current"/> : "Run"}
                                            </Button>
                                        </Modal.Footer>
                                    </Form>
                                )}
                            </Formik>
                        )}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}
