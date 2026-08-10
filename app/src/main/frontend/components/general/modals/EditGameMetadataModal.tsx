import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {
    Accordion,
    Button,
    Modal
} from "@heroui/react";
import {Form, Formik} from "formik";
import Input from "Frontend/components/general/input/Input";
import React, {useEffect, useState} from "react";
import GameUpdateDto from "Frontend/generated/org/gameyfin/app/games/dto/GameUpdateDto";
import GameEnumPropertyValuesDto from "Frontend/generated/org/gameyfin/app/games/dto/GameEnumPropertyValuesDto";
import {deepDiff} from "Frontend/util/utils";
import {GameEndpoint} from "Frontend/generated/endpoints";
import TextAreaInput from "Frontend/components/general/input/TextAreaInput";
import * as Yup from "yup";
import GameCoverPicker from "Frontend/components/general/input/GameCoverPicker";
import DatePickerInput from "Frontend/components/general/input/DatePickerInput";
import ArrayInput from "Frontend/components/general/input/ArrayInput";
import GameHeaderPicker from "Frontend/components/general/input/GameHeaderPicker";
import ArrayInputAutocomplete from "Frontend/components/general/input/ArrayInputAutocomplete";
import {useSnapshot} from "valtio/react";
import {platformState} from "Frontend/state/PlatformState";

interface EditGameMetadataModalProps {
    game: GameDto;
    isOpen: boolean;
    onOpenChange: (isOpen: boolean) => void;
}

export default function EditGameMetadataModal({game, isOpen, onOpenChange}: EditGameMetadataModalProps) {
    const availablePlatforms = useSnapshot(platformState).available;
    const [propertyEnumValues, setPropertyEnumValues] = useState<GameEnumPropertyValuesDto>();

    useEffect(() => {
        GameEndpoint.getEnumPropertyValues().then(setPropertyEnumValues);
    }, []);

    return propertyEnumValues && (
        <Modal>
            <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} variant="opaque">
                <Modal.Container size="lg" className="max-w-3xl">
                    <Modal.Dialog>
                        {({close}) => {

                            async function updateGame(values: GameUpdateDto) {
                                //@ts-ignore
                                const changed = deepDiff(game, values) as GameUpdateDto;
                                if (Object.keys(changed).length === 0) return;

                                changed.id = game.id;
                                await GameEndpoint.updateGame(changed);
                                close();
                            }

                            return (
                                <>
                                    <Modal.CloseTrigger/>
                                    <Formik initialValues={game}
                                            enableReinitialize={true}
                                            onSubmit={updateGame}
                                            validationSchema={Yup.object({
                                                title: Yup.string().required("Title is required")
                                            })}
                                    >
                                        {(formik: any) => (
                                            <Form>
                                                <Modal.Header className="flex flex-col gap-1">
                                                    <Modal.Heading>Update game metadata</Modal.Heading>
                                                </Modal.Header>
                                                <Modal.Body>
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
                                                    <ArrayInputAutocomplete options={Array.from(availablePlatforms)}
                                                                            name="platforms" label="Platforms"/>
                                                    <TextAreaInput key="summary" name="summary" label="Summary (HTML)"/>
                                                    <TextAreaInput key="comment" name="comment" label="Comment (Markdown)"/>
                                                    <Accordion variant="default" className="space-y-2">
                                                        <Accordion.Item id="additional-metadata"
                                                                        className="-mx-2 rounded-lg bg-surface-secondary">
                                                            <Accordion.Heading>
                                                                <Accordion.Trigger className="flex items-center justify-between">
                                                                    <span>Additional Metadata</span>
                                                                    <Accordion.Indicator/>
                                                                </Accordion.Trigger>
                                                            </Accordion.Heading>
                                                            <Accordion.Panel>
                                                                <Accordion.Body className="max-h-80 overflow-y-auto">
                                                                    <ArrayInput key="developers" name="developers" label="Developers"/>
                                                                    <ArrayInput key="publishers" name="publishers" label="Publishers"/>
                                                                    <ArrayInputAutocomplete options={propertyEnumValues.genres}
                                                                                            defaultSelected={game.genres}
                                                                                            key="genres" name="genres" label="Genres"/>
                                                                    <ArrayInputAutocomplete options={propertyEnumValues.themes}
                                                                                            defaultSelected={game.themes}
                                                                                            key="themes" name="themes" label="Themes"/>
                                                                    <ArrayInputAutocomplete options={propertyEnumValues.features}
                                                                                            defaultSelected={game.features}
                                                                                            key="features" name="features"
                                                                                            label="Features"/>
                                                                    <ArrayInputAutocomplete options={propertyEnumValues.perspectives}
                                                                                            defaultSelected={game.perspectives}
                                                                                            key="perspectives" name="perspectives"
                                                                                            label="Perspectives"/>
                                                                    <ArrayInput key="keywords" name="keywords" label="Keywords"/>
                                                                </Accordion.Body>
                                                            </Accordion.Panel>
                                                        </Accordion.Item>
                                                    </Accordion>
                                                </Modal.Body>
                                                <Modal.Footer>
                                                    <Button variant="tertiary" onPress={close}>
                                                        Cancel
                                                    </Button>
                                                    <Button
                                                        variant="primary"
                                                        isPending={formik.isSubmitting}
                                                        isDisabled={formik.isSubmitting || !formik.dirty}
                                                        type="submit"
                                                    >
                                                        {formik.isSubmitting ? "" : "Save"}
                                                    </Button>
                                                </Modal.Footer>
                                            </Form>
                                        )}
                                    </Formik>
                                </>
                            )
                        }}
                    </Modal.Dialog>
                </Modal.Container>
            </Modal.Backdrop>
        </Modal>
    );
}