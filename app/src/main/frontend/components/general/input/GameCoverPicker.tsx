import {Image, useDisclosure} from "@heroui/react";
import {GameCoverFallback} from "Frontend/components/general/covers/GameCoverFallback";
import React from "react";
import {useField} from "formik";
import {GameCoverPickerModal} from "Frontend/components/general/modals/GameCoverPickerModal";
import {Pencil} from "@phosphor-icons/react";


// @ts-ignore
export default function GameCoverPicker({game, label, showErrorUntouched = false, ...props}) {

    // @ts-ignore
    const [field] = useField(props);

    const gameCoverPickerModal = useDisclosure();

    return (<>
        <div className="relative group w-fit h-fit cursor-pointer"
             onClick={gameCoverPickerModal.onOpenChange}>
            <Image
                alt={game.title}
                className="z-0 object-cover aspect-[12/17] group-hover:brightness-50"
                src={field.value ? field.value : `images/cover/${game.coverId}`}
                {...props}
                {...field}
                radius="none"
                height={216}
                fallbackSrc={<GameCoverFallback title={game.title}
                                                size={216}
                                                radius="none"/>}
            />
            <div
                className="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100"
            >
                <Pencil size={46}/>
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