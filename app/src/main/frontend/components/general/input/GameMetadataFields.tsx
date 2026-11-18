import React from "react";
import {Accordion} from "@heroui/react";
import {FastField, FieldProps, FormikProvider} from "formik";
import Input from "Frontend/components/general/input/Input";
import MarkdownInput from "Frontend/components/general/input/MarkdownInput";
import DatePickerInput from "Frontend/components/general/input/DatePickerInput";
import ArrayInput from "Frontend/components/general/input/ArrayInput";
import GameMediaEditor from "Frontend/components/general/input/GameMediaEditor";
import AutocompleteInput from "Frontend/components/general/input/AutocompleteInput.tsx";
import {GameMetadataFormValues} from "Frontend/util/gameMedia";
import {useSnapshot} from "valtio/react";
import {platformState} from "Frontend/state/PlatformState";
import {GameMetadataAdminDto} from "Frontend/dtos/GameDtos";
import GameMetadataSourceIcon from "Frontend/components/general/game/GameMetadataSourceIcon";
import GameEnumPropertyValuesDto from "Frontend/generated/org/gameyfin/app/games/dto/GameEnumPropertyValuesDto";

function MetadataField({name, children}: { name: string; children: React.ReactNode }) {
    // Existing input components use useField. Scope their context to this FastField so
    // unrelated edits do not rerender every control in the metadata form.
    return <FastField name={name}>
        {({form}: FieldProps<unknown, GameMetadataFormValues>) =>
            <FormikProvider value={form}>{children}</FormikProvider>}
    </FastField>;
}

export default function GameMetadataFields({initialValues, propertyEnumValues, searchOnOpen = false}: {
    initialValues: GameMetadataFormValues;
    propertyEnumValues: GameEnumPropertyValuesDto;
    searchOnOpen?: boolean;
}) {
    const availablePlatforms = useSnapshot(platformState).available;
    function sourceIcon(field: string) {
        const metadata = initialValues.metadata as GameMetadataAdminDto;
        return <GameMetadataSourceIcon source={metadata?.fields?.[field]}/>;
    }
    return (
        <GameMediaEditor searchOnOpen={searchOnOpen} sourceIcon={sourceIcon}>
            {({artwork, gallery}) => <>
                <MetadataField name="metadata.path">
                    <Input key="metadata.path" name="metadata.path"
                           label="Path" labelSuffix={sourceIcon("path")}
                           isDisabled className="mb-0"/>
                </MetadataField>
                {artwork}
                <div className="flex flex-col sm:flex-row gap-4 mt-4">
                    <MetadataField name="title">
                        <Input key="title" name="title" label="Title"
                               labelSuffix={sourceIcon("title")}
                               isRequired/>
                    </MetadataField>
                    <MetadataField name="release">
                        <DatePickerInput key="release" name="release"
                                         label="Release"
                                         labelSuffix={sourceIcon("release")}
                                         className="w-fit"/>
                    </MetadataField>
                </div>
                <MetadataField name="platforms"
                               key={Array.from(availablePlatforms).join(",")}>
                    <AutocompleteInput
                        options={Array.from(availablePlatforms)}
                        name="platforms" label="Platforms"
                        labelSuffix={sourceIcon("platforms")}/>
                </MetadataField>
                <MetadataField name="comment">
                    <MarkdownInput key="comment" name="comment"
                                   label="Comment"
                                   labelSuffix={sourceIcon("comment")}/>
                </MetadataField>
                <MetadataField name="summary">
                    <MarkdownInput key="summary" name="summary"
                                   label="Summary"
                                   labelSuffix={sourceIcon("summary")}/>
                </MetadataField>
                {gallery}
                <Accordion variant="default" className="space-y-2">
                    <Accordion.Item id="additional-metadata">
                        <Accordion.Heading>
                            <Accordion.Trigger
                                className="bg-segment rounded-2xl mb-4">
                                <span>Additional Metadata</span>
                                <Accordion.Indicator/>
                            </Accordion.Trigger>
                        </Accordion.Heading>
                        <Accordion.Panel>
                            <Accordion.Body className="overflow-y-auto">
                                <MetadataField name="developers">
                                    <ArrayInput key="developers"
                                                name="developers"
                                                label="Developers"
                                                labelSuffix={sourceIcon("developers")}/>
                                </MetadataField>
                                <MetadataField name="publishers">
                                    <ArrayInput key="publishers"
                                                name="publishers"
                                                label="Publishers"
                                                labelSuffix={sourceIcon("publishers")}/>
                                </MetadataField>
                                <MetadataField name="genres">
                                    <AutocompleteInput
                                        options={propertyEnumValues.genres}
                                        defaultSelected={initialValues.genres}
                                        key="genres" name="genres"
                                        label="Genres"
                                        labelSuffix={sourceIcon("genres")}/>
                                </MetadataField>
                                <MetadataField name="themes">
                                    <AutocompleteInput
                                        options={propertyEnumValues.themes}
                                        defaultSelected={initialValues.themes}
                                        key="themes" name="themes"
                                        label="Themes"
                                        labelSuffix={sourceIcon("themes")}/>
                                </MetadataField>
                                <MetadataField name="features">
                                    <AutocompleteInput
                                        options={propertyEnumValues.features}
                                        defaultSelected={initialValues.features}
                                        key="features" name="features"
                                        label="Features"
                                        labelSuffix={sourceIcon("features")}/>
                                </MetadataField>
                                <MetadataField name="perspectives">
                                    <AutocompleteInput
                                        options={propertyEnumValues.perspectives}
                                        defaultSelected={initialValues.perspectives}
                                        key="perspectives"
                                        name="perspectives"
                                        label="Perspectives"
                                        labelSuffix={sourceIcon("perspectives")}/>
                                </MetadataField>
                                <MetadataField name="keywords">
                                    <ArrayInput key="keywords"
                                                name="keywords"
                                                label="Keywords"
                                                labelSuffix={sourceIcon("keywords")}/>
                                </MetadataField>
                            </Accordion.Body>
                        </Accordion.Panel>
                    </Accordion.Item>
                </Accordion>
            </>}
        </GameMediaEditor>
    );
}
