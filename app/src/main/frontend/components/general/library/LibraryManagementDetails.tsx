import LibraryAdminDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryAdminDto";
import {CheckIcon} from "@phosphor-icons/react";
import {Button, Spinner, toast, useOverlayState} from "@heroui/react";
import React from "react";
import ConfirmModal from "Frontend/components/general/modals/ConfirmModal";
import {Form, Formik} from "formik";
import {deepDiff} from "Frontend/util/utils";
import LibraryUpdateDto from "Frontend/generated/org/gameyfin/app/libraries/dto/LibraryUpdateDto";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import Input from "Frontend/components/general/input/Input";
import DirectoryMappingInput from "Frontend/components/general/input/DirectoryMappingInput";
import Section from "Frontend/components/general/Section";
import {useNavigate} from "react-router";
import * as Yup from "yup";
import AutocompleteInput from "Frontend/components/general/input/AutocompleteInput.tsx";
import {useSnapshot} from "valtio/react";
import {platformState} from "Frontend/state/PlatformState";
import CheckboxInput from "Frontend/components/general/input/CheckboxInput";

interface LibraryManagementDetailsProps {
    library: LibraryAdminDto;
}

export default function LibraryManagementDetails({library}: LibraryManagementDetailsProps) {
    const navigate = useNavigate();
    const [librarySaved, setLibrarySaved] = React.useState(false);
    const [isDeleting, setIsDeleting] = React.useState(false);
    const deleteModal = useOverlayState();
    const availablePlatforms = useSnapshot(platformState).available;

    async function handleSubmit(values: LibraryAdminDto): Promise<void> {
        const changed = deepDiff(library, values) as LibraryUpdateDto;

        if (Object.keys(changed).length === 0) return;

        changed.id = library.id;
        await LibraryEndpoint.updateLibrary(changed);
        setLibrarySaved(true);
        setTimeout(() => setLibrarySaved(false), 2000);
    }

    async function handleDelete(): Promise<void> {
        setIsDeleting(true);
        try {
            await LibraryEndpoint.deleteLibrary(library.id);

            toast.success("Library deleted", {
                description: `Library ${library.name} deleted!`
            });

            deleteModal.close();
            navigate("/administration/games");
        } catch (e) {
            toast.warning("Error deleting library", {
                description: `Library ${library.name} could not be deleted!`
            });
        } finally {
            setIsDeleting(false);
        }
    }

    return <Formik
        initialValues={library}
        onSubmit={handleSubmit}
        enableReinitialize={true}
        validationSchema={Yup.object({
            name: Yup.string()
                .required("Library name is required")
                .max(255, "Library name must be 255 characters or less"),
            directories: Yup.array()
                .of(Yup.object())
                .min(1, "At least one directory is required")
        })}
    >
        {(formik) => (
            <Form>
                <div className="flex flex-row grow justify-between mb-4">
                    <h1 className="text-2xl font-bold">Edit library details</h1>
                    <Button
                        variant="primary"
                        isPending={formik.isSubmitting}
                        isDisabled={formik.isSubmitting || librarySaved || !formik.dirty}
                        type="submit"
                    >
                        {formik.isSubmitting ? <Spinner size="sm" color="current"/> : librarySaved ?
                            <CheckIcon/> : "Save"}
                    </Button>
                </div>

                <Input label="Library name" name="name"/>

                <CheckboxInput label="Display on homepage" name="metadata.displayOnHomepage" className="mb-4"/>

                <AutocompleteInput options={Array.from(availablePlatforms)} name="platforms" label="Platforms"/>

                <DirectoryMappingInput name="directories"/>

                <Section title="Danger zone"/>
                <Button variant="danger" onPress={deleteModal.open}>
                    Delete library
                </Button>

                <ConfirmModal isOpen={deleteModal.isOpen}
                              onOpenChange={deleteModal.setOpen}
                              title="Delete library"
                              confirmLabel="Delete"
                              isPending={isDeleting}
                              onConfirm={handleDelete}>
                    <p>Delete library <code>{library.name}</code>? This can not be undone.</p>
                </ConfirmModal>
            </Form>
        )}
    </Formik>;
}