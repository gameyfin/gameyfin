import React, {memo, ReactNode, useEffect, useRef, useState} from "react";
import {Button} from "@heroui/react";
import {GridList, GridListItem} from "react-aria-components";
import {
    CaretUpDownIcon,
    DotsSixIcon,
    ImageIcon,
    PencilIcon,
    PlayIcon,
    TrashIcon,
    VideoIcon
} from "@phosphor-icons/react";
import {FormikHelpers, useFormikContext} from "formik";
import VideoPlayer from "Frontend/components/general/covers/VideoPlayer";
import AddGameVideoModal from "Frontend/components/general/modals/AddGameVideoModal";
import GameImagePickerModal from "Frontend/components/general/modals/GameImagePickerModal";
import {allDraftImages, DraftImage, GameMetadataFormValues, MAX_MEDIA_SIZE, MediaKind} from "Frontend/util/gameMedia";
import {orderMedia} from "Frontend/util/mediaOrder";
import GameMetadataSourceIcon from "Frontend/components/general/game/GameMetadataSourceIcon";
import {GameFieldMetadataDto, GameFieldMetadataType} from "Frontend/dtos/GameDtos";
import {useAuth} from "Frontend/util/auth";
import {useAnimatedReorder} from "Frontend/util/useAnimatedReorder";


const VideoPreview = memo(function VideoPreview({url, label}: { url: string; label: string }) {
    const container = useRef<HTMLDivElement>(null);
    const [visible, setVisible] = useState(false);

    useEffect(() => {
        // Video thumbnails may require an external request. Only start it near the viewport.
        if (!container.current) return;
        const observer = new IntersectionObserver(entries => {
            if (entries.some(entry => entry.isIntersecting)) {
                setVisible(true);
                observer.disconnect();
            }
        }, {rootMargin: "200px"});
        observer.observe(container.current);
        return () => observer.disconnect();
    }, []);

    return <div ref={container} role="img" aria-label={label}
                className="w-full aspect-video overflow-hidden rounded bg-black">
        <div inert className="pointer-events-none flex size-full items-center justify-center text-white">
            {visible ? <VideoPlayer src={url} width="100%" height="100%" light
                                    playing={false} controls={false} previewTabIndex={-1}
                                    playIcon={<PlayIcon weight="fill" size={32}/>}/>
                : <PlayIcon weight="fill" size={32}/>}
        </div>
    </div>;
});

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
    const {ref, dragAndDropHooks} = useAnimatedReorder({
        items, disabled,
        getKey: item => item.key,
        getLabel: item => item.label,
        onReorder: reordered => onChange(reordered.map(item => item.key))
    });

    return <GridList ref={ref} aria-label={label} items={items} selectionMode="none" dragAndDropHooks={dragAndDropHooks}
                     layout={layout}
                     className={layout === "grid" ? "grid grid-cols-2 gap-3 sm:grid-cols-3" : "flex flex-col gap-2"}>
        {item => <GridListItem id={item.key} textValue={item.label} data-reorder-key={item.key}
                               className={`group/media min-w-0 outline-none focus-visible:ring-2 focus-visible:ring-accent rounded-lg ${disabled ? "cursor-default" : "cursor-grab"}`}>
            <div data-reorder-content
                 className={`flex h-full min-w-0 items-center rounded-lg bg-foreground/5 p-2 group-data-dragging/media:opacity-40 ${layout === "grid" ? "flex-wrap gap-2" : "gap-3"}`}>
                {item.preview}
                <Button slot="drag" aria-label={`Reorder ${item.label}`} isDisabled={disabled}
                        isIconOnly size="sm" variant="ghost"
                        className={disabled ? "cursor-default" : "group-data-dragging/media:cursor-grabbing"}>
                    {layout === "grid" ? <DotsSixIcon size={20}/> : <CaretUpDownIcon size={20}/>}
                </Button>
                {item.content}
                <Button aria-label={`Delete ${item.label}`} isDisabled={disabled} onPress={() => onDelete(item.key)}
                        isIconOnly size="sm" variant="danger-soft" className="ml-auto">
                    <TrashIcon size={20}/>
                </Button>
            </div>
        </GridListItem>}
    </GridList>;
}

// Keep the drag-and-drop collection and video previews stable while other form fields change.
const MediaGallery = memo(function MediaGallery({
                                                    mediaImages,
                                                    mediaVideos,
                                                    mediaVideoSources,
                                                    mediaOrder,
                                                    isSubmitting,
                                                    setValues
                                                }: {
    mediaImages: GameMetadataFormValues["mediaImages"];
    mediaVideos: GameMetadataFormValues["mediaVideos"];
    mediaVideoSources: GameMetadataFormValues["mediaVideoSources"];
    mediaOrder: GameMetadataFormValues["mediaOrder"];
    isSubmitting: boolean;
    setValues: FormikHelpers<GameMetadataFormValues>["setValues"];
}) {
    const occurrences = new Map<string, number>();
    const videos = mediaVideos.map((url, index) => {
        const occurrence = occurrences.get(url) ?? 0;
        occurrences.set(url, occurrence + 1);
        return {key: `${url}-${occurrence}`, url, source: mediaVideoSources?.[index]};
    });
    const galleryItems = orderMedia<{
        key: string;
        kind: "image" | "video";
        image?: DraftImage;
        url?: string;
        source?: GameFieldMetadataDto
    }>(
        mediaImages.map(image => ({key: image.key, kind: "image", image})),
        videos.map(video => ({key: `video-${video.key}`, kind: "video", url: video.url, source: video.source})),
        mediaOrder
    ).map((item, index) => ({
        ...item,
        label: `${item.kind === "image" ? "screenshot" : "video"} ${index + 1}`,
        preview: item.kind === "image"
            ? <img src={item.image!.previewUrl} alt={`Screenshot ${index + 1}`} loading="lazy" decoding="async"
                   className="w-full aspect-video rounded object-cover"/>
            : <VideoPreview url={item.url!} label={`Video ${index + 1} preview`}/>,
        content: <div className="min-w-0 flex items-center flex-1 text-sm">
            <span className="-ml-4 mr-2 mb-0.5">
            <GameMetadataSourceIcon source={item.kind === "image" ? item.image?.source : item.source}/>
            </span>
            <span className="flex items-center gap-2" title={item.url}>
                {item.kind === "image" ? <ImageIcon/> : <VideoIcon/>} {index + 1}
            </span>
        </div>
    }));

    function changeGallery(keys: string[]) {
        const items = keys.map(key => galleryItems.find(item => item.key === key)!);
        void setValues(values => ({
            ...values,
            mediaImages: items.filter(item => item.kind === "image").map(item => item.image!),
            mediaVideos: items.filter(item => item.kind === "video").map(item => item.url!),
            mediaVideoSources: items.filter(item => item.kind === "video").map(item => item.source),
            mediaOrder: items.map(item => item.kind)
        }));
    }

    return galleryItems.length === 0 ? <p className="text-sm text-muted">No screenshots or videos added.</p> :
        <OrderedMediaList label="Screenshot and video order" disabled={isSubmitting} layout="grid"
                          items={galleryItems} onChange={changeGallery}
                          onDelete={key => changeGallery(galleryItems.filter(item => item.key !== key).map(item => item.key))}/>;
});

interface GameMediaEditorProps {
    searchOnOpen: boolean;
    sourceIcon?: (field: string) => ReactNode;
    children: (sections: { artwork: ReactNode; gallery: ReactNode }) => ReactNode;
}

export default function GameMediaEditor({children, searchOnOpen, sourceIcon}: GameMediaEditorProps) {
    const {values, setFieldValue, setValues, isSubmitting} = useFormikContext<GameMetadataFormValues>();
    const {state: auth} = useAuth();
    const [picker, setPicker] = useState<MediaKind>();
    const [videoPickerOpen, setVideoPickerOpen] = useState(false);
    const previews = useRef(new Set<string>());
    const images = allDraftImages(values);
    const gallerySources = new Map<string, GameFieldMetadataDto>();
    [...values.mediaImages.map(image => image.source), ...(values.mediaVideoSources ?? [])].forEach(source => {
        if (source?.source && source.type !== GameFieldMetadataType.UNKNOWN) {
            gallerySources.set(JSON.stringify([source.type, source.source]), source);
        }
    });

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

    function addVideo(url: string) {
        void setValues(values => ({
            ...values, mediaVideos: [...values.mediaVideos, url],
            mediaVideoSources: [...values.mediaVideos.map((_, index) => values.mediaVideoSources?.[index]),
                auth.user ? {
                    type: GameFieldMetadataType.USER,
                    source: auth.user.username,
                    updatedAt: new Date().toISOString()
                } : undefined],
            mediaOrder: [...orderMedia(
                values.mediaImages.map(() => "image"), values.mediaVideos.map(() => "video"), values.mediaOrder
            ), "video"]
        }));
    }

    const artwork = <div className="flex flex-col sm:flex-row gap-4">
        {(["cover", "header"] as const).map(kind => {
            const image = kind === "cover" ? values.mediaCover : values.mediaHeader;
            const label = kind === "cover" ? "cover" : "banner";
            return <div key={kind} className={`flex flex-col gap-2 ${kind === "header" ? "flex-1 min-w-0" : ""}`}>
                <span className="flex items-center">
                    {kind === "cover" ? "Cover" : "Banner"}
                    {sourceIcon?.(kind === "cover" ? "coverImage" : "headerImage")}
                </span>
                <button type="button" disabled={isSubmitting} onClick={() => setPicker(kind)}
                        aria-label={`Edit ${label}`}
                        className={`cursor-pointer relative group overflow-hidden rounded-lg bg-foreground/5 h-44 ${kind === "cover" ? "aspect-12/17" : "w-full"}`}>
                    {image ? <img src={image.previewUrl} alt={`Game ${label}`} decoding="async"
                                  className="size-full object-cover transition-[filter] group-hover:brightness-25 group-focus-visible:brightness-25"/>
                        : <span
                            className="flex flex-col items-center gap-2 group-hover:opacity-0 group-focus-visible:opacity-0"><ImageIcon
                            size={32}/>Add {label}</span>}
                    <span
                        className="absolute inset-0 flex flex-col items-center justify-center gap-2 text-center opacity-0 transition-opacity group-hover:opacity-100">
                        <PencilIcon size={46}/>
                        <span>Edit {label}</span>
                    </span>
                </button>
            </div>;
        })}
    </div>;
    const gallery = <div className="flex flex-col gap-5 my-4">
        <section aria-label="Media" className="flex flex-col gap-2">
            <div className="flex flex-wrap items-center justify-between gap-2">
                <h3 className="font-semibold flex items-center flex-wrap">Media
                    {[...gallerySources].map(([key, source]) => <GameMetadataSourceIcon key={key} source={source}/>)}
                </h3>
                <div className="flex flex-wrap gap-2">
                    <Button type="button" size="sm"
                            isDisabled={isSubmitting}
                            onPress={() => setPicker("screenshot")}>
                        Add screenshots
                    </Button>
                    <Button type="button" size="sm" isDisabled={isSubmitting}
                            onPress={() => setVideoPickerOpen(true)}>
                        Add video
                    </Button>
                </div>
            </div>

            <MediaGallery mediaImages={values.mediaImages} mediaVideos={values.mediaVideos}
                          mediaVideoSources={values.mediaVideoSources}
                          mediaOrder={values.mediaOrder} isSubmitting={isSubmitting} setValues={setValues}/>

        </section>
    </div>;
    return <>
        {children({artwork, gallery})}
        {videoPickerOpen && <AddGameVideoModal existingUrls={values.mediaVideos} onAdd={addVideo}
                                               onClose={() => setVideoPickerOpen(false)}/>}
        {picker && <GameImagePickerModal key={picker} game={values} kind={picker} onSelect={selectImages}
                                         searchOnOpen={searchOnOpen}
                                         excludedUrls={picker === "screenshot" ? values.mediaImages.map(image => image.selection.url).filter((url): url is string => !!url) : []}
                                         onClose={() => setPicker(undefined)}/>}
    </>;
}
