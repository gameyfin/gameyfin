import React, {useEffect} from "react";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import Section from "Frontend/components/general/Section";
import * as Yup from 'yup';
import {Button, Divider, Tooltip} from "@heroui/react";
import {Plus} from "@phosphor-icons/react";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/LibraryDto";
import {LibraryOverviewCard} from "Frontend/components/general/cards/LibraryOverviewCard";
import {ListData, useListData} from "@react-stately/data";

function LibraryManagementLayout({getConfig, formik}: any) {
    const libraries: ListData<LibraryDto> = useListData({});

    useEffect(() => {
        LibraryEndpoint.getAllLibraries().then(
            (response) => libraries.items = response as LibraryDto[]
        );
    }, []);

    return (
        <div className="flex flex-col">

            <Section title="Permissions"/>
            <ConfigFormField configElement={getConfig("library.allow-public-access")}/>

            <Section title="Scanning"/>
            <ConfigFormField configElement={getConfig("library.scan.enable-filesystem-watcher")}/>

            <Section title="Metadata"/>
            <div className="flex flex-row">
                <ConfigFormField configElement={getConfig("library.metadata.update.enabled")}/>
                <ConfigFormField configElement={getConfig("library.metadata.update.schedule")}
                                 isDisabled={!formik.values.library.metadata.update.enabled}/>
            </div>

            <div className="flex flex-row items-baseline justify-between">
                <h2 className={"text-xl font-bold mt-8 mb-1"}>Libraries</h2>
                <Tooltip content="Add new library">
                    <Button isIconOnly variant="flat" onPress={() => {
                        libraries.append({id: 1, name: "Library", path: "/path/to/library"} as LibraryDto)
                    }}>
                        <Plus/>
                    </Button>
                </Tooltip>
            </div>
            <Divider className="mb-4"/>
            {libraries.items.length > 0 ?
                <div className="grid grid-cols-300px gap-4">
                    {libraries.items.map((library) => <LibraryOverviewCard library={library} key={library.name}/>)}
                </div> :
                "No libraries configured. Add your first library!"
            }
        </div>
    );
}

const validationSchema = Yup.object({
    library: Yup.object({
        metadata: Yup.object({
            update: Yup.object({
                // @ts-ignore
                schedule: Yup.string().cron()
            })
        })
    })
});

export const LibraryManagement = withConfigPage(LibraryManagementLayout, "Library Management", "library", validationSchema);