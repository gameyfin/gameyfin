import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {
    Accordion,
    AccordionItem,
    Button,
    Modal,
    ModalBody,
    ModalContent,
    ModalFooter,
    ModalHeader
} from "@heroui/react";
import {Form, Formik} from "formik";
import Input from "Frontend/components/general/input/Input";
import React from "react";
import GameUpdateDto from "Frontend/generated/org/gameyfin/app/games/dto/GameUpdateDto";
import {deepDiff} from "Frontend/util/utils";
import {GameEndpoint} from "Frontend/generated/endpoints";
import TextAreaInput from "Frontend/components/general/input/TextAreaInput";
import * as Yup from "yup";
import GameCoverPicker from "Frontend/components/general/input/GameCoverPicker";
import DatePickerInput from "Frontend/components/general/input/DatePickerInput";
import ArrayInput from "Frontend/components/general/input/ArrayInput";
import GameHeaderPicker from "Frontend/components/general/input/GameHeaderPicker";

interface EditGameMetadataModalProps {
    game: GameDto;
    isOpen: boolean;
    onOpenChange: () => void;
}

export default function EditGameMetadataModal({game, isOpen, onOpenChange}: EditGameMetadataModalProps) {
    return (
        <Modal isOpen={isOpen} onOpenChange={onOpenChange} backdrop="opaque" size="3xl">
            <ModalContent>
                {(onClose) => {

                    async function updateGame(values: GameUpdateDto) {
                        //@ts-ignore
                        const changed = deepDiff(game, values) as GameUpdateDto;
                        if (Object.keys(changed).length === 0) return;

                        changed.id = game.id;
                        await GameEndpoint.updateGame(changed);
                        onClose();
                    }

                    return (
                        <Formik initialValues={game}
                                enableReinitialize={true}
                                onSubmit={updateGame}
                                validationSchema={Yup.object({
                                    title: Yup.string().required("Title is required")
                                })}
                        >
                            {(formik: any) => (
                                <Form>
                                    <ModalHeader className="flex flex-col gap-1">
                                        Update game metadata
                                    </ModalHeader>
                                    <ModalBody>
                                        <Input key="metadata.path" name="metadata.path" label="Path"
                                               isDisabled className="mb-0"/>
                                        <div className="flex flex-row gap-4 h-44">
                                            <GameCoverPicker key="coverUrl" name="coverUrl" game={game}/>
                                            <GameHeaderPicker key="headerUrl" name="headerUrl" game={game}/>
                                        </div>
                                        <div className="flex flex-row gap-4">
                                            <Input key="title" name="title" label="Title" isRequired/>
                                            <DatePickerInput key="release" name="release" label="Release"
                                                             className="w-fit"/>
                                        </div>
                                        <TextAreaInput key="summary" name="summary" label="Summary (HTML)"/>
                                        <TextAreaInput key="comment" name="comment" label="Comment (Markdown)"/>
                                        <Accordion variant="splitted"
                                                   itemClasses={{
                                                       base: "-mx-2",
                                                       content: "max-h-80 overflow-y-auto",
                                                   }}>
                                            <AccordionItem key="additional-metadata"
                                                           aria-label="Additional Metadata"
                                                           title="Additional Metadata">
                                                <ArrayInput key="developers" name="developers" label="Developers"/>
                                                <ArrayInput key="publishers" name="publishers" label="Publishers"/>
                                                <ArrayInput key="genres" name="genres" label="Genres"/>
                                                <ArrayInput key="themes" name="themes" label="Themes"/>
                                                <ArrayInput key="keywords" name="keywords" label="Keywords"/>
                                                <ArrayInput key="features" name="features" label="Features"/>
                                                <ArrayInput key="perspectives" name="perspectives"
                                                            label="Perspectives"/>
                                                <ArrayInput key="keywords" name="keywords"
                                                            label="Keywords"/>
                                            </AccordionItem>
                                        </Accordion>
                                    </ModalBody>
                                    <ModalFooter>
                                        <Button variant="light" onPress={onClose}>
                                            Cancel
                                        </Button>
                                        <Button
                                            color="primary"
                                            isLoading={formik.isSubmitting}
                                            isDisabled={formik.isSubmitting || !formik.dirty}
                                            type="submit"
                                        >
                                            {formik.isSubmitting ? "" : "Save"}
                                        </Button>
                                    </ModalFooter>
                                </Form>
                            )}
                        </Formik>
                    )
                }}
            </ModalContent>
        </Modal>
    );
}