import {Button, ListBox, Modal, Select, Spinner} from "@heroui/react";
import {Form, FormikProvider, useFormik} from "formik";
import * as Yup from "yup";
import {useSnapshot} from "valtio/react";
import {libraryState} from "Frontend/state/LibraryState";
import {GameEndpoint} from "Frontend/generated/endpoints";
import FileTreeView from "Frontend/components/general/input/FileTreeView";
import Input from "Frontend/components/general/input/Input";

interface AddGameModalProps {
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
    onCreated: (gameId: number) => void;
}

export default function ImportGameModal({isOpen, onOpenChange, onCreated}: AddGameModalProps) {
    const libraries = useSnapshot(libraryState).sorted;
    const formik = useFormik({
        initialValues: {title: "", path: "", libraryId: null as string | null},
        initialStatus: "",
        validationSchema: Yup.object({
            title: Yup.string().trim().required("Title is required"),
            path: Yup.string().trim().required("Select a game directory"),
            libraryId: Yup.string().nullable().required("Select a library"),
        }),
        validateOnMount: true,
        onSubmit: async (values, {setStatus}) => {
            setStatus("");
            try {
                const id = await GameEndpoint.createGame(values.title.trim(), values.path.trim(), Number(values.libraryId));
                onOpenChange(false);
                onCreated(id);
            } catch (e) {
                setStatus(e instanceof Error ? e.message : "Unable to add game");
            }
        },
    });
    const {isSubmitting, values, status} = formik;

    return (
        <FormikProvider value={formik}>
            <Modal>
                <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} isDismissable={!isSubmitting}>
                    <Modal.Container size="lg">
                        <Modal.Dialog>
                            {({close}) => (
                                <Form>
                                    <Modal.Header>
                                        <Modal.Heading>Import game manually</Modal.Heading>
                                    </Modal.Header>
                                    <Modal.Body className="flex flex-col">
                                        <p className="mb-2">
                                            Enter the game details, then add artwork and other metadata
                                            in the editor.
                                        </p>
                                        <Select aria-label="Library" placeholder="Select library"
                                                value={values.libraryId}
                                                onChange={value => formik.setFieldValue("libraryId", value === null ? null : String(value))}
                                                isDisabled={isSubmitting}
                                                className="mb-12">
                                            <Select.Trigger><Select.Value/><Select.Indicator/></Select.Trigger>
                                            <Select.Popover>
                                                <ListBox>
                                                    {libraries.map(library => (
                                                        <ListBox.Item key={library.id} id={String(library.id)}
                                                                      textValue={library.name}>
                                                            {library.name}<ListBox.ItemIndicator/>
                                                        </ListBox.Item>
                                                    ))}
                                                </ListBox>
                                            </Select.Popover>
                                        </Select>
                                        <Input name="title" aria-label="Title" placeholder="Title" isRequired
                                               isDisabled={isSubmitting}/>
                                        <Input name="path" aria-label="Selected path"
                                               placeholder="Select a game directory"
                                               isReadOnly isDisabled={isSubmitting}/>
                                        <div className="h-64 overflow-auto" inert={isSubmitting}>
                                            <FileTreeView onPathChange={path => formik.setFieldValue("path", path)}/>
                                        </div>
                                        {libraries.length === 0 && <p>Create a library before adding games.</p>}
                                        {status && <p role="alert" className="text-danger">{status}</p>}
                                    </Modal.Body>
                                    <Modal.Footer>
                                        <Button variant="ghost" onPress={close}
                                                isDisabled={isSubmitting}>Cancel</Button>
                                        <Button type="submit" variant="primary" isPending={isSubmitting}
                                                isDisabled={isSubmitting || !formik.isValid}>
                                            {isSubmitting ?
                                                <Spinner size="sm" color="current"/> : "Add and edit metadata"}
                                        </Button>
                                    </Modal.Footer>
                                </Form>
                            )}
                        </Modal.Dialog>
                    </Modal.Container>
                </Modal.Backdrop>
            </Modal>
        </FormikProvider>
    );
}
