import {Image, useDisclosure} from "@heroui/react";
import React from "react";
import {useField} from "formik";
import {GameCoverPickerModal} from "Frontend/components/general/modals/GameCoverPickerModal";
import { ImageBrokenIcon, PencilIcon } from "@phosphor-icons/react";


// @ts-ignore
export default function GameCoverPicker({game, showErrorUntouched = false, ...props}) {

    // @ts-ignore
    const [field] = useField(props);

    const gameCoverPickerModal = useDisclosure();

    return (<>
        <div className="relative group aspect-12/17 cursor-pointer bg-background/50"
             onClick={gameCoverPickerModal.onOpenChange}>
            {field.value || game.coverId ?
                <div className="size-full overflow-hidden">
                    <Image
                        alt={game.title}
                        className="z-0 object-cover group-hover:brightness-25"
                        src={field.value ? field.value : `images/cover/${game.coverId}`}
                        {...props}
                        {...field}
                        radius="none"
                    />
                </div> :
                <div
                    className="absolute inset-0 flex flex-col text-center items-center justify-center group-hover:opacity-0"
                >
                    <ImageBrokenIcon size={46}/>
                    <p>No cover image available</p>
                </div>}
            <div
                className="absolute inset-0 flex flex-col gap-2 text-center items-center justify-center opacity-0 group-hover:opacity-100"
            >
                <PencilIcon size={46}/>
                <p>Edit cover</p>
            </div>
        </div>
        <GameCoverPickerModal
            game={game}
            isOpen={gameCoverPickerModal.isOpen}
            onOpenChange={gameCoverPickerModal.onOpenChange}
            setCoverUrl={(coverUrl) => field.onChange({target: {name: field.name, value: coverUrl}})}
        />
    </>);
}