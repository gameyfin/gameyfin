import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
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
import GameUpdateDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameUpdateDto";
import {deepDiff} from "Frontend/util/utils";
import {GameEndpoint} from "Frontend/generated/endpoints";
import TextAreaInput from "Frontend/components/general/input/TextAreaInput";
import * as Yup from "yup";
import GameCoverPicker from "Frontend/components/general/input/GameCoverPicker";
import DatePickerInput from "Frontend/components/general/input/DatePickerInput";
import ArrayInput from "Frontend/components/general/input/ArrayInput";

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
                                        <div className="flex flex-row gap-8">
                                            {/*@ts-ignore*/}
                                            <GameCoverPicker key="coverUrl" name="coverUrl" game={game}/>
                                            <div className="flex flex-col flex-1">
                                                <Input key="metadata.path" name="metadata.path" label="Path"
                                                       isDisabled/>
                                                <Input key="title" name="title" label="Title" isRequired/>
                                                <DatePickerInput key="release" name="release" label="Release"/>
                                            </div>
                                        </div>
                                        <TextAreaInput key="summary" name="summary" label="Summary (HTML)"/>
                                        <TextAreaInput key="comment" name="comment" label="Comment (Markdown)"/>
                                        <Accordion>
                                            <AccordionItem key="additional-metadata"
                                                           aria-label="Additional Metadata"
                                                           title="Additional Metadata"
                                                           className="flex flex-col">
                                                <div className="flex flex-row gap-4">
                                                    <ArrayInput key="developers" name="developers" label="Developers"/>
                                                    <div className="w-0 border-s border-foreground/70"/>
                                                    <ArrayInput key="publishers" name="publishers" label="Publishers"/>
                                                </div>
                                                <div className="flex flex-row gap-4">
                                                    <ArrayInput key="genres" name="genres" label="Genres"/>
                                                    <div className="w-0 border-s border-foreground/70"/>
                                                    <ArrayInput key="themes" name="themes" label="Themes"/>
                                                </div>
                                                <div className="flex flex-row gap-4">
                                                    <ArrayInput key="keywords" name="keywords" label="Keywords"/>
                                                    <div className="w-0 border-s border-foreground/70"/>
                                                    <ArrayInput key="features" name="features" label="Features"/>
                                                </div>
                                                <div className="flex flex-row gap-4">
                                                    <ArrayInput key="perspectives" name="perspectives"
                                                                label="Perspectives"/>
                                                    <div className="w-0 border-s border-foreground/70"/>
                                                    <div className="flex-1"/>
                                                </div>
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