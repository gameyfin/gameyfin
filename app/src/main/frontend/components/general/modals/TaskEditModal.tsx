import React from "react";
import {Button, Modal, Spinner, toast} from "@heroui/react";
import {Form, Formik, getIn, setIn} from "formik";
import * as Yup from "yup";
import "Frontend/util/yup-extensions";
import {TaskEndpoint} from "Frontend/generated/endpoints";
import type TaskDto from "Frontend/generated/org/gameyfin/app/core/tasks/dto/TaskDto";
import type PluginConfigMetadataDto from "Frontend/generated/org/gameyfin/app/core/plugins/dto/PluginConfigMetadataDto";
import Input from "Frontend/components/general/input/Input";
import CheckboxInput from "Frontend/components/general/input/CheckboxInput";
import PluginConfigFormField from "Frontend/components/general/plugin/PluginConfigFormField";

interface TaskEditModalProps {
    task: TaskDto;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    onSaved: (task: TaskDto) => void;
}

interface TaskFormValues {
    schedule: string;
    scheduleEnabled: boolean;
    settings: Record<string, unknown>;
}

const SCHEDULE_DESCRIPTION =
    "Cron expression with six fields: second, minute, hour, day of month, month and day of week " +
    "(e.g. \"0 0 3 * * *\" for every day at 3:00). Macros like @hourly, @daily, @weekly and @monthly are also supported. " +
    "Leave empty to use the default schedule.";

function settingFieldName(key: string) {
    return `settings.${key}`;
}

function toFormValue(meta: PluginConfigMetadataDto, value: unknown): unknown {
    if (value == null) return meta.type.toLowerCase() === "boolean" ? false : "";
    switch (meta.type.toLowerCase()) {
        case "float":
        case "int":
            return Number(value);
        case "boolean":
            return value === true || value === "true";
        default:
            return value.toString();
    }
}

export default function TaskEditModal({task, isOpen, onOpenChange, onSaved}: TaskEditModalProps) {

    function initialValues(): TaskFormValues {
        let values: TaskFormValues = {
            schedule: task.schedule ?? "",
            scheduleEnabled: task.scheduleEnabled,
            settings: {}
        };
        for (const meta of task.settingsMetadata) {
            values = setIn(values, settingFieldName(meta.key), toFormValue(meta, task.settings[meta.key] ?? meta.default));
        }
        return values;
    }

    const validationSchema = Yup.object({
        schedule: Yup.string().cron().test(
            "schedule-required",
            "A schedule is required to enable the scheduled execution",
            (value, context) => !context.parent.scheduleEnabled || !!value?.trim() || !!task.defaultSchedule
        )
    });

    async function save(values: TaskFormValues, close: () => void) {
        const settings: Record<string, string | undefined> = {};
        for (const meta of task.settingsMetadata) {
            const value = getIn(values, settingFieldName(meta.key));
            settings[meta.key] = value == null || value === "" ? undefined : String(value);
        }

        try {
            const updated = await TaskEndpoint.updateTask({
                key: task.key,
                schedule: values.schedule.trim() || undefined,
                scheduleEnabled: task.schedulable && values.scheduleEnabled,
                settings: task.settingsMetadata.length > 0 ? settings : undefined
            });
            toast.success("Task saved", {description: `Configuration of ${task.name} saved`});
            onSaved(updated);
            close();
        } catch (e: any) {
            toast.danger("Could not save task", {description: e?.message});
        }
    }

    return (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange}>
                <Modal.Container size="lg">
                    <Modal.Dialog>
                        {({close}) => (
                            <Formik initialValues={initialValues()}
                                    validationSchema={validationSchema}
                                    enableReinitialize={true}
                                    onSubmit={(values) => save(values, close)}>
                                {(formik) => (
                                    <Form>
                                        <Modal.Header>
                                            <Modal.Heading>{task.name}</Modal.Heading>
                                        </Modal.Header>
                                        <Modal.Body className="flex flex-col gap-2">
                                            {task.description && <p className="text-sm text-muted">{task.description}</p>}

                                            {task.schedulable && <>
                                                <h4 className="text-l font-bold mt-2">Schedule</h4>
                                                <CheckboxInput label="Run on a schedule" name="scheduleEnabled"/>
                                                <Input label="Cron expression"
                                                       name="schedule"
                                                       variant="secondary"
                                                       placeholder={task.defaultSchedule ?? "e.g. 0 0 3 * * *"}
                                                       description={SCHEDULE_DESCRIPTION}
                                                       showErrorUntouched={true}/>
                                            </>}

                                            {task.settingsMetadata.length > 0 && <>
                                                <h4 className="text-l font-bold mt-2">Settings</h4>
                                                {task.settingsMetadata.map(meta => (
                                                    <PluginConfigFormField
                                                        key={meta.key}
                                                        pluginConfigMetadata={meta}
                                                        name={settingFieldName(meta.key)}
                                                        description={meta.description}
                                                        variant="secondary"/>
                                                ))}
                                            </>}
                                        </Modal.Body>
                                        <Modal.Footer>
                                            <Button variant="ghost" onPress={close}>Cancel</Button>
                                            <Button variant="primary"
                                                    type="submit"
                                                    isPending={formik.isSubmitting}
                                                    isDisabled={formik.isSubmitting || !formik.dirty}>
                                                {formik.isSubmitting ? <Spinner size="sm" color="current"/> : "Save"}
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
