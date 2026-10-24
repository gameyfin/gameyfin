import React, {useEffect, useRef, useState} from "react";
import {Button, Input, Modal, Spinner} from "@heroui/react";
import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import {GameEndpoint} from "Frontend/generated/endpoints";
import {DraftImage, IMAGE_ACCEPT, MediaKind, remoteImage, uploadedImages} from "Frontend/util/gameMedia";
import PluginIcon from "Frontend/components/general/plugin/PluginIcon";
import {useSnapshot} from "valtio/react";
import {pluginState} from "Frontend/state/PluginState";
import {ArrowRightIcon, CheckIcon, UploadSimpleIcon} from "@phosphor-icons/react";

interface Props {
    game: GameDto;
    kind: MediaKind;
    onSelect: (images: DraftImage[]) => void;
    onClose: () => void;
    excludedUrls?: string[];
}

interface SearchImage {
    url: string;
    title: string;
    pluginId: string
}

export default function GameImagePickerModal({game, kind, onSelect, onClose, excludedUrls = []}: Props) {
    const multiple = kind === "screenshot";
    const label = kind === "header" ? "banner" : kind;
    const [url, setUrl] = useState("");
    const [term, setTerm] = useState(game.title);
    const [results, setResults] = useState<SearchImage[]>([]);
    const [selected, setSelected] = useState<string[]>([]);
    const [searching, setSearching] = useState(false);
    const [uploading, setUploading] = useState(false);
    const [error, setError] = useState("");
    const fileInput = useRef<HTMLInputElement>(null);
    const request = useRef(0);
    const mounted = useRef(true);
    const searchInFlight = useRef(false);
    const plugins = useSnapshot(pluginState).state;

    useEffect(() => {
        mounted.current = true;
        void search(game.title);
        return () => {
            mounted.current = false;
            request.current++;
            searchInFlight.current = false;
        };
    }, []);

    async function search(query: string) {
        if (searchInFlight.current || uploading || !query.trim()) return;
        searchInFlight.current = true;
        const current = ++request.current;
        setSearching(true);
        setError("");
        try {
            const matches = await GameEndpoint.getPotentialMatches(query, game.platforms);
            if (!mounted.current || current !== request.current) return;
            const urls = new Set<string>();
            const images = matches.flatMap(match => {
                const candidates = kind === "cover" ? match.coverUrls : kind === "header" ? match.headerUrls : match.screenshotUrls;
                return (candidates ?? []).filter(candidate => {
                    if (urls.has(candidate.url)) return false;
                    urls.add(candidate.url);
                    return true;
                }).map(candidate => ({...candidate, title: match.title}));
            });
            setResults(images);
            setSelected([]);
        } catch (error) {
            if (mounted.current && current === request.current) setError(error instanceof Error ? error.message : "Image search failed. Try again.");
        } finally {
            if (current === request.current) {
                searchInFlight.current = false;
                if (mounted.current) setSearching(false);
            }
        }
    }

    function selectUrls(urls: string[]) {
        try {
            onSelect(urls.map(remoteImage));
            onClose();
        } catch (error) {
            setError(error instanceof Error ? error.message : "Enter a valid image URL.");
        }
    }

    async function upload(files: File[]) {
        if (!files.length) return;
        setUploading(true);
        setError("");
        try {
            const drafts = await uploadedImages(multiple ? files : files.slice(0, 1));
            if (!mounted.current) {
                drafts.forEach(image => URL.revokeObjectURL(image.previewUrl));
                return;
            }
            onSelect(drafts);
            onClose();
        } catch (error) {
            if (mounted.current) setError(error instanceof Error ? error.message : "Image upload failed.");
        } finally {
            if (mounted.current) setUploading(false);
        }
    }

    return <Modal>
        <Modal.Backdrop isOpen onOpenChange={open => {
            if (!open) onClose();
        }}>
            <Modal.Container size="lg" className="max-w-3xl">
                <Modal.Dialog>
                    <Modal.CloseTrigger/>
                    <Modal.Header><Modal.Heading>Choose {multiple ? "screenshots" : `a ${label}`}</Modal.Heading></Modal.Header>
                    <Modal.Body className="flex flex-col gap-4">
                        <div className="flex flex-row items-center gap-2">
                            <span className="text-sm text-muted"><span
                                className="text-foreground">Upload your own. </span>
                                JPEG, PNG, WebP or GIF. Up to 20 MiB per image.</span>
                            <Button type="button" variant="ghost" onPress={() => fileInput.current?.click()}
                                    isPending={uploading}>
                                {uploading ? <Spinner size="sm" color="current"/> : <UploadSimpleIcon/>}
                                Upload {multiple ? "images" : "an image"}
                            </Button>
                            <input ref={fileInput} type="file" accept={IMAGE_ACCEPT} multiple={multiple}
                                   className="hidden" disabled={uploading}
                                   onChange={event => {
                                       void upload(Array.from(event.target.files ?? []));
                                       event.target.value = "";
                                   }}/>
                        </div>
                        <div className="flex gap-2">
                            <Input aria-label="Image URL" placeholder="Image URL" value={url} className="flex-1 min-w-0"
                                   onChange={event => setUrl(event.target.value)}
                                   onKeyDown={event => {
                                       if (event.key === "Enter") {
                                           event.preventDefault();
                                           selectUrls([url]);
                                       }
                                   }}/>
                            <Button type="button" isDisabled={!url.trim() || uploading}
                                    onPress={() => selectUrls([url])}>Use URL</Button>
                        </div>
                        <div className="flex gap-2">
                            <Input aria-label="Search games for images" placeholder="Search games" value={term}
                                   className="flex-1 min-w-0"
                                   onChange={event => setTerm(event.target.value)}
                                   onKeyDown={event => {
                                       if (event.key === "Enter") {
                                           event.preventDefault();
                                           void search(term);
                                       }
                                   }}/>
                            <Button type="button" isPending={searching}
                                    isDisabled={!term.trim() || searching || uploading}
                                    aria-label="Search" onPress={() => {
                                void search(term);
                            }}>
                                {({isPending}) => <>{isPending && <Spinner size="sm" color="current"/>}Search</>}
                            </Button>
                        </div>
                        {error && <p role="alert" className="text-danger">{error}</p>}
                        {!searching && results.length === 0 && <p>No images found.</p>}
                        <div
                            className={`grid gap-3 overflow-y-auto ${kind === "cover" ? "grid-cols-3 sm:grid-cols-4" : "grid-cols-2"}`}>
                            {results.map(image => {
                                const added = excludedUrls.includes(image.url);
                                const checked = selected.includes(image.url);
                                return <button key={image.url} type="button" disabled={added || uploading}
                                               aria-label={`${multiple ? "Select" : "Use"} ${label} for ${image.title}`}
                                               aria-pressed={multiple ? checked : undefined}
                                               className={`cursor-pointer group rounded-lg border-2 p-1 text-left ${checked ? "border-accent" : "border-transparent"} disabled:opacity-40`}
                                               onClick={() => multiple
                                                   ? setSelected(values => checked ? values.filter(url => url !== image.url) : [...values, image.url])
                                                   : selectUrls([image.url])}>
                                    <span className="relative block overflow-hidden rounded">
                                        <img src={image.url} alt={image.title} loading="lazy"
                                             className={`w-full object-cover transition-[filter] group-hover:brightness-25 group-focus-visible:brightness-25 ${kind === "cover" ? "aspect-12/17" : "aspect-video"}`}/>
                                        <span
                                            className="absolute inset-0 flex flex-col items-center justify-center gap-2 p-2 text-center text-white opacity-0 transition-opacity group-hover:opacity-100">
                                            {plugins[image.pluginId] &&
                                                <PluginIcon plugin={plugins[image.pluginId]} size={32} blurred={false}
                                                            showTooltip={false}/>}
                                            <span className="text-sm">{image.title}</span>
                                            {checked ? <CheckIcon size={24}/> : <ArrowRightIcon size={24}/>}
                                        </span>
                                        {(added || checked) && <span
                                            className="absolute top-1 right-1 flex items-center gap-1 rounded bg-accent px-2 py-1 text-xs text-accent-foreground">
                                            <CheckIcon/>{added ? "Added" : "Selected"}
                                        </span>}
                                    </span>
                                </button>;
                            })}
                        </div>
                    </Modal.Body>
                    <Modal.Footer>
                        <Button type="button" variant="ghost" onPress={onClose}>Cancel</Button>
                        {multiple && <Button type="button" variant="primary" isDisabled={!selected.length || uploading}
                                             onPress={() => selectUrls(selected)}>Add selected
                            ({selected.length})</Button>}
                    </Modal.Footer>
                </Modal.Dialog>
            </Modal.Container>
        </Modal.Backdrop>
    </Modal>;
}
