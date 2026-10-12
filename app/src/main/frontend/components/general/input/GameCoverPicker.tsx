import {useOverlayState} from "@heroui/react";
import React from "react";
import {useField} from "formik";
import {GameCoverPickerModal} from "Frontend/components/general/modals/GameCoverPickerModal";
import {ImageBrokenIcon, PencilIcon} from "@phosphor-icons/react";
import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";

interface GameCoverPickerProps {
    game: GameDto;
    name: string;
    showErrorUntouched?: boolean;
}

export default function GameCoverPicker({game, showErrorUntouched = false, ...props}: GameCoverPickerProps) {

    const [field] = useField(props.name);

    const gameCoverPickerModal = useOverlayState();

    return (<>
        <div className="relative group aspect-12/17 cursor-pointer bg-background/50"
             onClick={gameCoverPickerModal.toggle}>
            {field.value || game.cover?.id ?
                <div className="size-full overflow-hidden">
                    <img
                        alt={game.title}
                        className="z-0 size-full object-cover rounded-none group-hover:brightness-25"
                        src={field.value ? field.value : `images/cover/${game.cover?.id}`}
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
            onOpenChange={gameCoverPickerModal.setOpen}
            setCoverUrl={(coverUrl) => field.onChange({target: {name: field.name, value: coverUrl}})}
        />
    </>);
}