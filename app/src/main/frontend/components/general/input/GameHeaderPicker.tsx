import {Image, useDisclosure} from "@heroui/react";
import React from "react";
import {useField} from "formik";
import {ImageBroken, Pencil} from "@phosphor-icons/react";
import {GameHeaderPickerModal} from "Frontend/components/general/modals/GameHeaderPickerModal";


// @ts-ignore
export default function GameHeaderPicker({game, showErrorUntouched = false, ...props}) {

    // @ts-ignore
    const [field] = useField(props);

    const gameHeaderPickerModal = useDisclosure();

    return (<>
        <div className="relative group size-full cursor-pointer bg-background/50"
             onClick={gameHeaderPickerModal.onOpenChange}>
            {field.value || game.headerId ?
                <div className="size-full overflow-hidden">
                    <Image
                        alt={game.title}
                        className="z-0 object-cover group-hover:brightness-[25%]"
                        src={field.value ? field.value : `images/cover/${game.headerId}`}
                        {...props}
                        {...field}
                        radius="none"
                    />
                </div> :
                <div
                    className="absolute inset-0 flex flex-col text-center items-center justify-center group-hover:opacity-0"
                >
                    <ImageBroken size={46}/>
                    <p>No header image available</p>
                </div>}
            <div
                className="absolute inset-0 flex flex-col gap-2 text-center items-center justify-center opacity-0 group-hover:opacity-100"
            >
                <Pencil size={46}/>
                <p>Edit header image</p>
            </div>
        </div>
        <GameHeaderPickerModal
            game={game}
            isOpen={gameHeaderPickerModal.isOpen}
            onOpenChange={gameHeaderPickerModal.onOpenChange}
            setHeaderUrl={(headerUrl) => field.onChange({target: {name: field.name, value: headerUrl}})}
        />
    </>);
}