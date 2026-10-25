import React, {ReactNode, useEffect, useRef, useState} from "react";
import {Button, Input} from "@heroui/react";
import {GridList, GridListItem, useDragAndDrop} from "react-aria-components";
import {
    CaretUpDownIcon,
    DotsSixIcon,
    ImageIcon,
    PencilIcon,
    PlayIcon,
    TrashIcon,
    VideoIcon
} from "@phosphor-icons/react";
import {useFormikContext} from "formik";
import ReactPlayerModule from "react-player";
import GameImagePickerModal from "Frontend/components/general/modals/GameImagePickerModal";
import {allDraftImages, DraftImage, GameMetadataFormValues, MAX_MEDIA_SIZE, MediaKind} from "Frontend/util/gameMedia";
import {orderMedia} from "Frontend/util/mediaOrder";

const ReactPlayer = ((ReactPlayerModule as any).default ?? ReactPlayerModule) as typeof ReactPlayerModule;

interface OrderedItem {
    key: string;
    label: string;
    content: ReactNode;
    preview?: ReactNode;
}

function OrderedMediaList({label, items, onChange, onDelete, disabled, layout = "stack"}: {
    label: string;
    items: OrderedItem[];
    onChange: (keys: string[]) => void;
    onDelete: (key: string) => void;
    disabled: boolean;
    layout?: "stack" | "grid";
}) {
    const {dragAndDropHooks} = useDragAndDrop({
        isDisabled: disabled,
        getItems: keys => [...keys].map(key => ({"text/plain": items.find(item => item.key === key)!.label})),
        getAllowedDropOperations: () => ["move"],
        onReorder(event) {
            if (event.keys.has(event.target.key)) return;
            const moved = items.filter(item => event.keys.has(item.key));
            const remaining = items.filter(item => !event.keys.has(item.key));
            const target = remaining.findIndex(item => item.key === event.target.key);
            if (target < 0) return;
            remaining.splice(target + (event.target.dropPosition === "after" ? 1 : 0), 0, ...moved);
            onChange(remaining.map(item => item.key));
        }
    });

    return <GridList aria-label={label} items={items} selectionMode="none" dragAndDropHooks={dragAndDropHooks}
                     layout={layout}
                     className={layout === "grid" ? "grid grid-cols-2 gap-3 sm:grid-cols-3" : "flex flex-col gap-2"}>
        {item => <GridListItem id={item.key} textValue={item.label}
                               className={`group/media flex min-w-0 items-center rounded-lg bg-foreground/5 p-2 outline-none focus-visible:ring-2 focus-visible:ring-accent data-dragging:opacity-60 ${disabled ? "cursor-default" : "cursor-grab active:cursor-grabbing data-dragging:cursor-grabbing"} ${layout === "grid" ? "flex-wrap gap-2" : "gap-3"}`}>
            {item.preview}
            <Button slot="drag" aria-label={`Reorder ${item.label}`} isDisabled={disabled}
                    isIconOnly size="sm" variant="ghost"
                    className={disabled ? "cursor-default" : "cursor-grab active:cursor-grabbing group-data-[dragging]/media:cursor-grabbing"}>
                {layout === "grid" ? <DotsSixIcon size={20}/> : <CaretUpDownIcon size={20}/>}
            </Button>
            {item.content}
            <Button aria-label={`Delete ${item.label}`} isDisabled={disabled} onPress={() => onDelete(item.key)}
                    isIconOnly size="sm" variant="danger-soft" className="ml-auto">
                <TrashIcon size={20}/>
            </Button>
        </GridListItem>}
    </GridList>;
}

interface GameMediaEditorProps {
    children: (sections: { artwork: ReactNode; gallery: ReactNode }) => ReactNode;
}

export default function GameMediaEditor({children}: GameMediaEditorProps) {
    const {values, setFieldValue, setValues, isSubmitting} = useFormikContext<GameMetadataFormValues>();
    const [picker, setPicker] = useState<MediaKind>();
    const [videoUrl, setVideoUrl] = useState("");
    const [videoError, setVideoError] = useState("");
    const previews = useRef(new Set<string>());
    const images = allDraftImages(values);

    useEffect(() => {
        const active = new Set(images.filter(image => image.file).map(image => image.previewUrl));
        previews.current.forEach(url => {
            if (!active.has(url)) URL.revokeObjectURL(url);
        });
        previews.current = active;
    });
    useEffect(() => () => {
        previews.current.forEach(url => URL.revokeObjectURL(url));
    }, []);

    function selectImages(selected: DraftImage[]) {
        const retained = picker === "cover" ? images.filter(image => image !== values.mediaCover)
            : picker === "header" ? images.filter(image => image !== values.mediaHeader) : images;
        if ([...retained, ...selected].reduce((size, image) => size + (image.file?.size ?? 0), 0) > MAX_MEDIA_SIZE) {
            selected.filter(image => image.file).forEach(image => URL.revokeObjectURL(image.previewUrl));
            throw new Error("The selected uploads exceed 100 MiB. Save fewer images at a time.");
        }
        if (picker === "screenshot") {
            const keys = new Set(values.mediaImages.map(image => image.key));
            void setFieldValue("mediaImages", [...values.mediaImages, ...selected.filter(image => !keys.has(image.key))]);
        } else {
            void setFieldValue(picker === "cover" ? "mediaCover" : "mediaHeader", selected[0]);
        }
    }

    function addVideo() {
        const url = videoUrl.trim();
        try {
            const parsed = new URL(url);
            if (!["http:", "https:"].includes(parsed.protocol) || !ReactPlayer.canPlay?.(url)) {
                throw new Error("Enter an HTTP(S) video URL supported by the video player.");
            }
            if (values.mediaVideos.includes(url)) throw new Error("This video has already been added.");
            void setValues({
                ...values, mediaVideos: [...values.mediaVideos, url],
                mediaOrder: [...galleryItems.map(item => item.kind), "video"]
            });
            setVideoUrl("");
            setVideoError("");
        } catch (error) {
            setVideoError(error instanceof TypeError ? "Enter a valid video URL." : (error as Error).message);
        }
    }

    const occurrences = new Map<string, number>();
    const videos = values.mediaVideos.map(url => {
        const occurrence = occurrences.get(url) ?? 0;
        occurrences.set(url, occurrence + 1);
        return {key: `${url}-${occurrence}`, url};
    });
    const galleryItems = orderMedia<{ key: string; kind: "image" | "video"; image?: DraftImage; url?: string }>(
        values.mediaImages.map(image => ({key: image.key, kind: "image", image})),
        videos.map(video => ({key: `video-${video.key}`, kind: "video", url: video.url})),
        values.mediaOrder
    ).map((item, index) => ({
        ...item,
        label: `${item.kind === "image" ? "screenshot" : "video"} ${index + 1}`,
        preview: item.kind === "image"
            ? <img src={item.image!.previewUrl} alt={`Screenshot ${index + 1}`}
                   className="w-full aspect-video rounded object-cover"/>
            : <div role="img" aria-label={`Video ${index + 1} preview`}
                   className="w-full aspect-video overflow-hidden rounded bg-black">
                <div inert className="pointer-events-none size-full">
                    <ReactPlayer src={item.url} width="100%" height="100%" light
                                 playing={false} controls={false} previewTabIndex={-1}
                                 playIcon={<PlayIcon weight="fill" size={32}/>}/>
                </div>
            </div>,
        content: <span className="min-w-0 flex flex-row gap-2 flex-1 truncate text-sm"
                       title={item.url}>{item.kind === "image" ? <ImageIcon/> : <VideoIcon/>} {index + 1}</span>
    }));

    function changeGallery(keys: string[]) {
        const items = keys.map(key => galleryItems.find(item => item.key === key)!);
        void setValues({
            ...values,
            mediaImages: items.filter(item => item.kind === "image").map(item => item.image!),
            mediaVideos: items.filter(item => item.kind === "video").map(item => item.url!),
            mediaOrder: items.map(item => item.kind)
        });
    }

    const artwork = <div className="flex gap-4 h-44">
        {(["cover", "header"] as const).map(kind => {
            const image = kind === "cover" ? values.mediaCover : values.mediaHeader;
            const label = kind === "cover" ? "cover" : "banner";
            return <button key={kind} type="button" disabled={isSubmitting} onClick={() => setPicker(kind)}
                           aria-label={`Edit ${label}`}
                           className={`cursor-pointer relative group overflow-hidden rounded-lg bg-foreground/5 ${kind === "cover" ? "aspect-12/17" : "flex-1"}`}>
                {image ? <img src={image.previewUrl} alt={`Game ${label}`}
                              className="size-full object-cover transition-[filter] group-hover:brightness-25 group-focus-visible:brightness-25"/>
                    : <span
                        className="flex flex-col items-center gap-2 group-hover:opacity-0 group-focus-visible:opacity-0"><ImageIcon
                        size={32}/>Add {label}</span>}
                <span
                    className="absolute inset-0 flex flex-col items-center justify-center gap-2 text-center opacity-0 transition-opacity group-hover:opacity-100">
                        <PencilIcon size={46}/>
                        <span>Edit {label}</span>
                    </span>
            </button>;
        })}
    </div>;
    const gallery = <div className="flex flex-col gap-5 my-4">
        <section aria-label="Media" className="flex flex-col gap-2">
            <div className="flex items-center justify-between gap-2">
                <h3 className="font-semibold">Media</h3>
                <Button type="button" size="sm" isDisabled={isSubmitting} onPress={() => setPicker("screenshot")}>Add
                    screenshots</Button>
            </div>
            {galleryItems.length === 0 ? <p className="text-sm text-muted">No screenshots or videos added.</p> :
                <OrderedMediaList label="Screenshot and video order" disabled={isSubmitting} layout="grid"
                                  items={galleryItems} onChange={changeGallery}
                                  onDelete={key => changeGallery(galleryItems.filter(item => item.key !== key).map(item => item.key))}/>
            }
        </section>
        <section aria-label="Videos" className="flex flex-col gap-2">
            <div className="flex gap-2">
                <Input aria-label="Video URL" placeholder="Video URL" value={videoUrl} disabled={isSubmitting}
                       className="flex-1 min-w-0"
                       onChange={event => setVideoUrl(event.target.value)}
                       onKeyDown={event => {
                           if (event.key === "Enter") {
                               event.preventDefault();
                               addVideo();
                           }
                       }}/>
                <Button type="button" isDisabled={!videoUrl.trim() || isSubmitting} onPress={addVideo}>Add
                    video</Button>
            </div>
            {videoError && <p role="alert" className="text-sm text-danger">{videoError}</p>}
        </section>
    </div>;
    return <>
        {children({artwork, gallery})}
        {picker && <GameImagePickerModal key={picker} game={values} kind={picker} onSelect={selectImages}
                                         excludedUrls={picker === "screenshot" ? values.mediaImages.map(image => image.selection.url).filter((url): url is string => !!url) : []}
                                         onClose={() => setPicker(undefined)}/>}
    </>;
}
