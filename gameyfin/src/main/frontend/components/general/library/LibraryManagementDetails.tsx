import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryDto";
import {Check} from "@phosphor-icons/react";
import {addToast, Button} from "@heroui/react";
import React from "react";
import {Form, Formik} from "formik";
import {deepDiff} from "Frontend/util/utils";
import LibraryUpdateDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryUpdateDto";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import Input from "Frontend/components/general/input/Input";
import DirectoryMappingInput from "Frontend/components/general/input/DirectoryMappingInput";
import Section from "Frontend/components/general/Section";
import {useNavigate} from "react-router";
import * as Yup from "yup";

interface LibraryManagementDetailsProps {
    library: LibraryDto;
}

export default function LibraryManagementDetails({library}: LibraryManagementDetailsProps) {
    const navigate = useNavigate();
    const [librarySaved, setLibrarySaved] = React.useState(false);

    async function handleSubmit(values: LibraryDto): Promise<void> {
        const changed = deepDiff(library, values) as LibraryUpdateDto;

        if (Object.keys(changed).length === 0) return;

        changed.id = library.id;
        await LibraryEndpoint.updateLibrary(changed);
        setLibrarySaved(true);
        setTimeout(() => setLibrarySaved(false), 2000);
    }

    async function handleDelete(): Promise<void> {
        try {
            await LibraryEndpoint.deleteLibrary(library.id);

            addToast({
                title: "Library deleted",
                description: `Library ${library.name} deleted!`,
                color: "success"
            });

            navigate("/administration/libraries");
        } catch (e) {
            addToast({
                title: "Error deleting library",
                description: `Library ${library.name} could not be deleted!`,
                color: "warning"
            });
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
                <div className="flex flex-row flex-grow justify-between mb-4">
                    <h1 className="text-2xl font-bold">Edit library details</h1>
                    <Button
                        color="primary"
                        isLoading={formik.isSubmitting}
                        isDisabled={formik.isSubmitting || librarySaved || !formik.dirty}
                        type="submit"
                    >
                        {formik.isSubmitting ? "" : librarySaved ? <Check/> : "Save"}
                    </Button>
                </div>

                <Input label="Library name" name="name"/>

                <DirectoryMappingInput name="directories"/>

                <Section title="Danger zone"/>
                <Button color="danger" onPress={handleDelete}>
                    Delete library
                </Button>
            </Form>
        )}
    </Formik>;
}