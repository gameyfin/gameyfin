import React, {useEffect, useMemo, useRef, useState} from "react";
import {
    Alert,
    Button,
    Chip,
    Description,
    Label,
    ListBox,
    Modal,
    ProgressBar,
    SearchField,
    Select,
    Spinner,
    Surface,
    Tooltip
} from "@heroui/react";
import {
    ArrowRightIcon,
    ArrowClockwiseIcon,
    CheckIcon,
    FileArrowUpIcon,
    FolderIcon,
    MagnifyingGlassIcon,
    PencilSimpleIcon,
    UploadSimpleIcon,
    XIcon
} from "@phosphor-icons/react";
import {useNavigate} from "react-router";
import ResponsiveDialog from "Frontend/components/layout/ResponsiveDialog";
import {libraryState, useFullLibrary} from "Frontend/state/LibraryState";
import {useSnapshot} from "valtio/react";
import {gameState, gameStore} from "Frontend/state/GameState";
import {GameEndpoint} from "Frontend/generated/endpoints";
import GameSearchResultDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSearchResultDto";
import GameUpdateDto from "Frontend/generated/org/gameyfin/app/games/dto/GameUpdateDto";
import {GameMetadataFormValues, metadataFormData, metadataUpdate} from "Frontend/util/gameMedia";
import {GameUploadStatus, uploadFile, uploadMetadataValues, uploadRequest} from "Frontend/util/gameUpload";
import {humanFileSize} from "Frontend/util/utils";
import GameSearchResults from "./GameSearchResults";
import Wizard from "Frontend/components/wizard/Wizard";
import WizardStep from "Frontend/components/wizard/WizardStep";
import GameMetadataFields from "Frontend/components/general/input/GameMetadataFields";
import GameEnumPropertyValuesDto from "Frontend/generated/org/gameyfin/app/games/dto/GameEnumPropertyValuesDto";
import {FormikHelpers} from "formik";
import * as Yup from "yup";

export default function UploadGameButton() {
    const navigate = useNavigate();
    const [open, setOpen] = useState(false);
    const libraries = useSnapshot(libraryState);
    const [libraryId, setLibraryId] = useState<number>();
    const {full: library} = useFullLibrary(open ? libraryId : undefined);
    const [metadataSource, setMetadataSource] = useState<GameSearchResultDto>();
    const [fileRevision, setFileRevision] = useState(0);
    const [propertyEnumValues, setPropertyEnumValues] = useState<GameEnumPropertyValuesDto>();
    const preparedFor = useRef("");
    const preparedSource = useRef("");
    const initialValues = useMemo(() => uploadMetadataValues({
        id: 0,
        title: "",
        platforms: []
    }, libraryId ?? 0, ""), [libraryId]);
    const [directory, setDirectory] = useState("");
    const [files, setFiles] = useState<File[]>([]);
    const [folder, setFolder] = useState(false);
    const [searchTerm, setSearchTerm] = useState("");
    const [results, setResults] = useState<GameSearchResultDto[]>();
    const [pending, setPending] = useState<string>();
    const busy = pending !== undefined;
    const [error, setError] = useState("");
    const [draft, setDraft] = useState<GameMetadataFormValues>();
    const [progress, setProgress] = useState<{ bytes: number; publishing: boolean }>();
    const session = useRef<string | undefined>(undefined);
    const abort = useRef<AbortController | undefined>(undefined);
    const fileInput = useRef<HTMLInputElement>(null);
    const folderInput = useRef<HTMLInputElement>(null);
    const name = folder ? files[0]?.webkitRelativePath.split("/")[0] : files[0]?.name;
    const total = files.reduce((size, file) => size + file.size, 0);
    const selectedDirectory = directory || (library?.directories.length === 1 ? library.directories[0].internalPath : "");
    const path = `${selectedDirectory.replace(/[\\/]+$/, "")}/${name ?? ""}`;
    const destinationKey = libraryId !== undefined && selectedDirectory && name
        ? JSON.stringify([libraryId, selectedDirectory, name]) : "";
    const [destinationCheck, setDestinationCheck] = useState<{
        key: string; status: "checking" | "available" | "error"; message?: string;
    }>();
    const destinationRequest = useRef(0);
    const destinationReady = !!library && !!destinationKey && destinationCheck?.key === destinationKey
        && destinationCheck.status === "available";

    async function checkDestination(signal?: AbortSignal): Promise<boolean> {
        if (!destinationKey) return false;
        const request = ++destinationRequest.current;
        setDestinationCheck({key: destinationKey, status: "checking"});
        try {
            await uploadRequest<void>("/destination", {
                method: "POST", headers: {"Content-Type": "application/json"},
                body: JSON.stringify({libraryId, directory: selectedDirectory, name}), signal
            });
            if (signal?.aborted || request !== destinationRequest.current) return false;
            setDestinationCheck({key: destinationKey, status: "available"});
            return true;
        } catch (error) {
            if (!signal?.aborted && request === destinationRequest.current) setDestinationCheck({
                key: destinationKey, status: "error",
                message: error instanceof Error ? error.message : "Unable to check this destination. Choose another path or try again."
            });
            return false;
        }
    }

    useEffect(() => {
        if (!open || !destinationKey) {
            setDestinationCheck(undefined);
            return;
        }
        const controller = new AbortController();
        void checkDestination(controller.signal);
        return () => {
            controller.abort();
            destinationRequest.current++;
        };
    }, [open, destinationKey]);

    useEffect(() => {
        if (open && !propertyEnumValues) {
            GameEndpoint.getEnumPropertyValues().then(setPropertyEnumValues)
                .catch(() => setError("Unable to load metadata fields. Close and reopen the upload dialog to retry."));
        }
    }, [open]);

    useEffect(() => () => {
        abort.current?.abort();
        if (session.current) void uploadRequest(`/${session.current}`, {
            method: "DELETE",
            keepalive: true
        }).catch(() => {
        });
    }, []);

    useEffect(() => {
        if (!open) return;
        const preventLoss = (event: BeforeUnloadEvent) => {
            if (session.current) event.preventDefault();
        };
        window.addEventListener("beforeunload", preventLoss);
        return () => window.removeEventListener("beforeunload", preventLoss);
    }, [open]);

    function close() {
        abort.current?.abort();
        if (session.current) void uploadRequest(`/${session.current}`, {method: "DELETE"}).catch(() => {
        });
        session.current = undefined;
        setOpen(false);
        setDraft(undefined);
        setFiles([]);
        setDirectory("");
        setLibraryId(undefined);
        setDestinationCheck(undefined);
        setResults(undefined);
        setSearchTerm("");
        setError("");
        setProgress(undefined);
        setMetadataSource(undefined);
        preparedFor.current = "";
        preparedSource.current = "";
    }

    function choose(selected: FileList | null, isFolder: boolean) {
        const chosen = Array.from(selected ?? []);
        setError("");
        if (chosen.length === 0) return;
        const root = chosen[0].webkitRelativePath.split("/")[0];
        if (isFolder && chosen.some(file => !file.webkitRelativePath.startsWith(`${root}/`))) {
            setError("Select one folder per game.");
            return;
        }
        setFileRevision(value => value + 1);
        if (!draft) setMetadataSource(undefined);
        setFiles(chosen);
        setFolder(isFolder);
        setSearchTerm(isFolder ? root : chosen[0].name.replace(/\.[^.]+$/, ""));
        setResults(undefined);
    }

    async function search() {
        if (!library || !name || !selectedDirectory || !searchTerm.trim()) return;
        setPending("search");
        setError("");
        try {
            setResults(await GameEndpoint.getPotentialMatches(searchTerm.trim(), library.platforms));
        } catch (e) {
            setError(e instanceof Error ? e.message : "Search failed. Try again.");
        } finally {
            setPending(undefined);
        }
    }

    async function prepare(values: GameMetadataFormValues, bag: FormikHelpers<GameMetadataFormValues>) {
        if (!library || libraryId === undefined || !name || !selectedDirectory) throw new Error("Choose game files and a destination first.");
        const originalIds = metadataSource?.originalIds ?? {};
        const sourceKey = JSON.stringify(originalIds);
        const uploadKey = JSON.stringify([fileRevision, libraryId, selectedDirectory, sourceKey]);
        if (preparedFor.current === uploadKey && draft) return;
        setPending("prepare");
        setError("");
        try {
            if (session.current) await uploadRequest(`/${session.current}`, {method: "DELETE"});
            session.current = undefined;
            const created = await uploadRequest<GameUploadStatus>("", {
                method: "POST", headers: {"Content-Type": "application/json"},
                body: JSON.stringify({
                    libraryId, directory: selectedDirectory, name, folder,
                    files: files.map(file => ({
                        path: folder ? file.webkitRelativePath.slice(name.length + 1) : file.name, size: file.size
                    }))
                })
            });
            session.current = created.id;
            const metadata = await uploadRequest<GameUpdateDto>(`/${created.id}/metadata`, {
                method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify(originalIds)
            });
            const nextDraft = uploadMetadataValues(metadata, libraryId, path);
            // Going back to change files or destination must not discard metadata edits.
            await bag.setValues(draft && preparedSource.current === sourceKey
                ? {...values, libraryId, platforms: draft.libraryId === libraryId ? values.platforms : nextDraft.platforms,
                    metadata: {...values.metadata, path}} as GameMetadataFormValues
                : nextDraft);
            setDraft(nextDraft);
            preparedFor.current = uploadKey;
            preparedSource.current = sourceKey;
        } finally {
            setPending(undefined);
        }
    }

    async function finish(values: GameMetadataFormValues) {
        if (!draft) throw new Error("Prepare metadata before uploading.");
        setPending("upload");
        try {
            await save(metadataUpdate(draft, values), values);
        } finally {
            setPending(undefined);
        }
    }

    async function save(metadata: GameUpdateDto, values: GameMetadataFormValues) {
        const id = session.current;
        if (!id) throw new Error("Upload session expired. Close and reopen the upload dialog.");
        // Validate artwork before transferring potentially large game files.
        const body = metadataFormData(metadata, values);
        let status = await uploadRequest<GameUploadStatus>(`/${id}`);
        if (!status.gameId) {
            const controller = new AbortController();
            abort.current = controller;
            setProgress({bytes: 0, publishing: false});
            try {
                await uploadRequest(`/${id}/restart`, {method: "POST", signal: controller.signal});
                let transferred = 0;
                for (let index = 0; index < files.length; index++) {
                    const previous = transferred;
                    await uploadFile(id, index, files[index], controller.signal,
                        bytes => setProgress({bytes: previous + bytes, publishing: false}));
                    transferred += files[index].size;
                }
                if (controller.signal.aborted) throw new Error("Upload cancelled.");
                setProgress({bytes: total, publishing: true});
                try {
                    status = await uploadRequest<GameUploadStatus>(`/${id}/complete`, {method: "POST", body});
                } catch (e) {
                    // A lost response may follow a successful commit. Check before offering a retry.
                    const completed = await uploadRequest<GameUploadStatus>(`/${id}`).catch(() => undefined);
                    if (!completed?.gameId) throw e;
                    status = completed;
                }
            } catch (e) {
                await uploadRequest(`/${id}/restart`, {method: "POST"}).catch(() => {
                });
                throw e;
            } finally {
                abort.current = undefined;
                setProgress(undefined);
            }
        }
        if (!status.gameId) throw new Error("Publication did not finish. Try again.");
        await gameStore.resync();
        if (!gameState.state[status.gameId]) {
            throw new Error("Game saved, but its details could not be loaded. Retry to open the saved game.");
        }
        close();
        navigate(`/game/${status.gameId}`);
    }

    const uploadStatus = progress && <div className="px-6" aria-live="polite">
        <Surface variant="secondary" className="flex flex-col gap-3 rounded-xl p-4">
            <ProgressBar aria-label="Game upload progress" value={total ? progress.bytes : 1}
                         maxValue={total || 1} isIndeterminate={progress.publishing}>
                <Label>{progress.publishing ? "Saving metadata…" : "Uploading game"}</Label>
                {!progress.publishing && <ProgressBar.Output/>}
                <ProgressBar.Track><ProgressBar.Fill/></ProgressBar.Track>
            </ProgressBar>
            <div className="flex flex-wrap items-center justify-between gap-2">
                <p className="text-xs text-muted tabular-nums">{humanFileSize(progress.bytes)} of {humanFileSize(total)}</p>
                {!progress.publishing &&
                    <Button type="button" size="sm" variant="danger-soft" onPress={() => abort.current?.abort()}>
                        <XIcon aria-hidden="true"/>Cancel upload
                    </Button>}
            </div>
        </Surface>
    </div>;

    return <>
        <Button type="button" variant="secondary" onPress={() => setOpen(true)}><UploadSimpleIcon/>Upload game</Button>
        {open && <Modal>
            <Modal.Backdrop isOpen onOpenChange={value => {
                if (!value && !busy) close();
            }} isDismissable={!busy}>
                <ResponsiveDialog size="lg" className="max-w-4xl">
                    <Modal.Dialog>
                        <Modal.CloseTrigger isDisabled={busy}/>
                        <Modal.Header className="flex-row items-center gap-3">
                            <span
                                className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-accent-soft text-accent">
                                <UploadSimpleIcon size={24} aria-hidden="true"/>
                            </span>
                            <div>
                                <Modal.Heading>Upload game</Modal.Heading>
                                <p className="text-sm text-muted mt-1">Add a game
                                    to {library?.name ?? "your library"}.</p>
                            </div>
                        </Modal.Header>
                        <Wizard variant="modal" keepStepsMounted initialValues={initialValues}
                                           isBusy={busy} onCancel={close} finishLabel="Upload and save"
                                           finishIcon={<UploadSimpleIcon aria-hidden="true"/>} status={uploadStatus}
                                           onSubmit={finish}>
                            <WizardStep title="Files" icon={<FileArrowUpIcon aria-hidden="true"/>}
                                        isNextDisabled={!name}>
                                <section className="flex flex-col gap-3" aria-labelledby="upload-files-heading">
                                    <h3 id="upload-files-heading" className="font-semibold">Game files</h3>
                                    <Surface variant="secondary"
                                             className="rounded-2xl border border-dashed border-border p-5">
                                        <div className="flex flex-col sm:flex-row sm:items-center gap-4">
                                            <span
                                                className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-default text-muted">
                                                {folder ? <FolderIcon size={28} aria-hidden="true"/> :
                                                    <FileArrowUpIcon size={28} aria-hidden="true"/>}
                                            </span>
                                            <div className="min-w-0 flex-1">
                                                <p className="font-medium wrap-anywhere">{name ?? "Choose a file or folder"}</p>
                                                {name ? <div className="mt-2 flex flex-wrap gap-2">
                                                        <Chip size="sm"
                                                              variant="soft">{files.length} {files.length === 1 ? "file" : "files"}</Chip>
                                                        <Chip size="sm" variant="soft">{humanFileSize(total)}</Chip>
                                                    </div> :
                                                    <p className="text-sm text-muted mt-1">One file or folder becomes
                                                        one game.</p>}
                                            </div>
                                            <div className="flex flex-wrap gap-2">
                                                <Button type="button" variant="secondary" isDisabled={busy}
                                                        onPress={() => fileInput.current?.click()}>
                                                    <FileArrowUpIcon aria-hidden="true"/>Choose file
                                                </Button>
                                                <Button type="button" variant="secondary" isDisabled={busy}
                                                        onPress={() => folderInput.current?.click()}>
                                                    <FolderIcon aria-hidden="true"/>Choose folder
                                                </Button>
                                            </div>
                                        </div>
                                    </Surface>
                                    <input type="file" ref={fileInput} className="hidden" aria-label="Choose game file"
                                           onChange={event => {
                                               choose(event.target.files, false);
                                               event.target.value = "";
                                           }}/>
                                    <input type="file" ref={element => {
                                        folderInput.current = element;
                                        element?.setAttribute("webkitdirectory", "");
                                    }} className="hidden" aria-label="Choose game folder" multiple
                                           onChange={event => {
                                               choose(event.target.files, true);
                                               event.target.value = "";
                                           }}/>
                                </section>
                            </WizardStep>
                            <WizardStep title="Destination" icon={<FolderIcon aria-hidden="true"/>}
                                        isNextDisabled={!destinationReady} onSubmit={() => checkDestination()}>
                                <section className="flex flex-col gap-3" aria-labelledby="upload-destination-heading">
                                    <h3 id="upload-destination-heading" className="font-semibold">Destination</h3>
                                    <Select aria-label="Target library" placeholder="Select a library"
                                            value={libraryId === undefined ? null : String(libraryId)}
                                            isDisabled={busy || !libraries.isLoaded} className="w-full"
                                            onChange={value => {
                                                setLibraryId(value === null ? undefined : Number(value));
                                                setDirectory("");
                                                setResults(undefined);
                                                setError("");
                                            }}>
                                        <Label>Target library</Label>
                                        <Select.Trigger className="items-center gap-2">
                                            <FolderIcon className="shrink-0 text-muted" aria-hidden="true"/>
                                            <Select.Value className="min-w-0 truncate"/>
                                            <Select.Indicator/>
                                        </Select.Trigger>
                                        <Select.Popover>
                                            <ListBox aria-label="Libraries">
                                                {libraries.sorted.map(item => <ListBox.Item key={item.id}
                                                    id={String(item.id)} textValue={item.name}>
                                                    {item.name}<ListBox.ItemIndicator/>
                                                </ListBox.Item>)}
                                            </ListBox>
                                        </Select.Popover>
                                        <Description>Choose the library that will contain this game.</Description>
                                    </Select>
                                    {libraries.isLoaded && libraries.sorted.length === 0 && <p className="text-sm text-muted">
                                        Create a library in Game Management before uploading games.
                                    </p>}
                                    {libraryId !== undefined && !library && <Spinner size="sm" aria-label="Loading library directories"/>}
                                    {library && (library.directories.length > 1 ? <Select
                                            aria-label="Library directory" placeholder="Select a library directory"
                                            value={selectedDirectory || null}
                                            onChange={value => setDirectory(String(value ?? ""))}
                                            isDisabled={busy} className="w-full">
                                            <Label className="sr-only">Library directory</Label>
                                            <Select.Trigger className="items-center gap-2">
                                                <FolderIcon className="shrink-0 text-muted" aria-hidden="true"/>
                                                <Select.Value className="min-w-0 truncate"/>
                                                <Select.Indicator/>
                                            </Select.Trigger>
                                            <Select.Popover>
                                                <ListBox aria-label="Library directories">
                                                    {library.directories.map(item =>
                                                        <ListBox.Item key={item.internalPath}
                                                                      id={item.internalPath}
                                                                      textValue={item.internalPath}>
                                                            <Label className="min-w-0 wrap-anywhere font-mono text-xs">
                                                                {item.internalPath}
                                                            </Label>
                                                            <ListBox.ItemIndicator/>
                                                        </ListBox.Item>
                                                    )}
                                                </ListBox>
                                            </Select.Popover>
                                            <Description>Choose which library folder will store this game.</Description>
                                        </Select> :
                                        <Surface variant="secondary" className="flex items-center gap-3 rounded-xl p-3">
                                            <FolderIcon className="shrink-0 text-muted" aria-hidden="true"/>
                                            <p className="font-mono text-xs wrap-anywhere">{selectedDirectory || "No directory configured"}</p>
                                        </Surface>)}
                                    {name && selectedDirectory &&
                                        <div className="flex items-center gap-2 text-xs text-muted">
                                            <ArrowRightIcon className="shrink-0" aria-hidden="true"/>
                                            <p className="font-mono wrap-anywhere">{path}</p>
                                        </div>}
                                    {destinationCheck?.key === destinationKey && destinationCheck.status === "checking" &&
                                        <div className="flex items-center gap-2 text-sm text-muted" role="status">
                                            <Spinner size="sm"/>Checking destination…
                                        </div>}
                                    {destinationCheck?.key === destinationKey && destinationCheck.status === "error" &&
                                        <Alert status="danger" className="rounded-xl border border-danger/40 bg-danger-soft p-4">
                                            <Alert.Indicator/>
                                            <Alert.Content>
                                                <Alert.Title className="font-semibold">Destination unavailable</Alert.Title>
                                                <Alert.Description className="flex flex-col gap-2">
                                                    <p>{destinationCheck.message}</p>
                                                    <p className="font-mono text-xs wrap-anywhere">{path}</p>
                                                    <p>Choose another library or path, or go back and select a different file or folder.</p>
                                                </Alert.Description>
                                                <Button type="button" size="sm" variant="secondary" className="mt-3 w-fit"
                                                        onPress={() => { void checkDestination(); }}>
                                                    <ArrowClockwiseIcon aria-hidden="true"/>Check again
                                                </Button>
                                            </Alert.Content>
                                        </Alert>}
                                </section>
                            </WizardStep>
                            <WizardStep title="Metadata" icon={<MagnifyingGlassIcon aria-hidden="true"/>}
                                        isNextDisabled={!propertyEnumValues} nextLabel="Review metadata"
                                        onSubmit={prepare}>
                                <section className="flex min-w-0 flex-col gap-3"
                                         aria-labelledby="upload-metadata-heading">
                                    <div className="flex flex-wrap items-center justify-between gap-2">
                                        <h3 id="upload-metadata-heading" className="font-semibold">Game metadata</h3>
                                        {results && <Chip size="sm"
                                                          variant="soft">{results.length} {results.length === 1 ? "match" : "matches"}</Chip>}
                                    </div>
                                    <p className="text-sm text-muted">Choose a search result or start with blank
                                        metadata. Review and edit it in the next step.</p>
                                    <div className="flex items-end gap-2">
                                        <SearchField aria-label="Search game metadata" value={searchTerm}
                                                     onChange={setSearchTerm}
                                                     isDisabled={busy} onKeyDown={event => {
                                            if (event.key === "Enter") {
                                                event.preventDefault();
                                                event.stopPropagation();
                                                void search();
                                            }
                                        }} className="min-w-0 flex-1">
                                            <SearchField.Group>
                                                <SearchField.SearchIcon/>
                                                <SearchField.Input placeholder="Search for a game…"/>
                                                <SearchField.ClearButton/>
                                            </SearchField.Group>
                                        </SearchField>
                                        <Tooltip>
                                            <Button type="button" isIconOnly variant="primary"
                                                    aria-label="Search metadata"
                                                    isDisabled={busy || !name || !selectedDirectory || !searchTerm.trim()}
                                                    isPending={pending === "search"} onPress={search}>
                                                {pending === "search" ? <Spinner size="sm" color="current"/> :
                                                    <MagnifyingGlassIcon/>}
                                            </Button>
                                            <Tooltip.Content>Search metadata</Tooltip.Content>
                                        </Tooltip>
                                    </div>
                                    <div className="flex flex-wrap items-center gap-3">
                                        <Button type="submit" variant="secondary"
                                                isDisabled={busy || !propertyEnumValues}
                                                onPress={() => setMetadataSource(undefined)}>
                                            <PencilSimpleIcon aria-hidden="true"/>Enter manually
                                        </Button>
                                        <p className="text-sm text-muted wrap-anywhere" role="status">
                                            {metadataSource ? `Selected: ${metadataSource.title}` : "Start with the file or folder name."}
                                        </p>
                                    </div>
                                    {results && <GameSearchResults results={results} actions={result => <Tooltip>
                                        <Button isIconOnly size="sm" type="submit"
                                                variant={metadataSource?.id === result.id ? "secondary" : "primary"}
                                                aria-label={`Use metadata for ${result.title}`}
                                                isDisabled={busy || !propertyEnumValues} onPress={() => setMetadataSource(result)}>
                                            {metadataSource?.id === result.id ? <CheckIcon/> : <ArrowRightIcon/>}
                                        </Button>
                                        <Tooltip.Content>{metadataSource?.id === result.id ? "Selected" : "Use this metadata"}</Tooltip.Content>
                                    </Tooltip>}/>}
                                </section>
                                {error && <Alert status="danger" className="mt-4">
                                    <Alert.Indicator/>
                                    <Alert.Content><Alert.Description>{error}</Alert.Description></Alert.Content>
                                </Alert>}
                            </WizardStep>
                            <WizardStep title="Review" icon={<PencilSimpleIcon aria-hidden="true"/>}
                                        validationSchema={Yup.object({title: Yup.string().trim().required("Title is required")})}>
                                {draft && propertyEnumValues && <div className="flex flex-col gap-4">
                                    <div>
                                        <h3 className="font-semibold">Review metadata</h3>
                                        <p className="text-sm text-muted mt-1">Check the details before uploading. Files
                                            stay in your browser until you finish.</p>
                                    </div>
                                    <GameMetadataFields initialValues={draft} propertyEnumValues={propertyEnumValues}/>
                                </div>}
                            </WizardStep>
                        </Wizard>
                    </Modal.Dialog>
                </ResponsiveDialog>
            </Modal.Backdrop>
        </Modal>}
    </>;
}
