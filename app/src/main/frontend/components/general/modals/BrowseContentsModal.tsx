import {useEffect, useMemo, useState} from "react";
import {Button, Checkbox, Modal} from "@heroui/react";
import {FileIcon, FolderIcon, FolderOpenIcon} from "@phosphor-icons/react";
import TreeView, {INode, NodeId} from "react-accessible-treeview";
import {humanFileSize} from "Frontend/util/utils";
import ComboButton from "Frontend/components/general/input/ComboButton";
import {downloadSelection} from "Frontend/endpoints/DownloadEndpoint";
import type DownloadProviderDto from "Frontend/generated/org/gameyfin/app/core/download/provider/DownloadProviderDto";

type FileEntry = {path: string; size: number};
type ContentsNode = INode<FileEntry> & {metadata: FileEntry};

function fileTree(files: FileEntry[]): ContentsNode[] {
    const root: ContentsNode = {id: 0, name: "", children: [], parent: null, metadata: {path: "", size: 0}};
    const nodes = new Map([["", root]]);
    for (const {path, size} of files) {
        let parent = root;
        let prefix = "";
        for (const name of path.split("/")) {
            prefix = prefix ? `${prefix}/${name}` : name;
            let node = nodes.get(prefix);
            if (!node) {
                node = {id: nodes.size, name, children: [], parent: parent.id, metadata: {path: prefix, size: 0}};
                nodes.set(prefix, node);
                parent.children.push(node.id);
            }
            node.metadata.size += size;
            parent = node;
        }
    }
    return [...nodes.values()];
}

interface Props {
    gameId: number;
    providers: DownloadProviderDto[];
    onClose: () => void;
}

export default function BrowseContentsModal({gameId, providers, onClose}: Props) {
    const [contents, setContents] = useState<{nodes: ContentsNode[]; fileCount: number; etag: string | null}>();
    const nodes = contents?.nodes;
    const [failed, setFailed] = useState(false);
    const [selectedIds, setSelectedIds] = useState<Set<NodeId>>(new Set());

    useEffect(() => {
        const controller = new AbortController();
        async function load() {
            try {
                const response = await fetch(`/download/${gameId}/contents`, {signal: controller.signal});
                if (!response.ok) throw new Error();
                const files: FileEntry[] = await response.json();
                setContents({nodes: fileTree(files), fileCount: files.length, etag: response.headers.get("ETag")});
            } catch {
                if (!controller.signal.aborted) setFailed(true);
            }
        }
        void load();
        return () => controller.abort();
    }, [gameId]);

    const selectedFiles = useMemo(() => {
        if (!nodes) return [];
        return Array.from(selectedIds, id => nodes[Number(id)])
            .filter(node => node.children.length === 0)
            .map(node => node.metadata);
    }, [nodes, selectedIds]);
    const selectedBytes = selectedFiles.reduce((total, file) => total + file.size, 0);

    const options = Object.fromEntries(providers.map(provider => [provider.key, {
        label: provider.name,
        description: provider.shortDescription ?? provider.description,
        isDisabled: selectedFiles.length === 0,
        action: () => {
            const selection = contents?.etag && selectedFiles.length === contents.fileCount
                ? {contentsETag: contents.etag}
                : {files: selectedFiles.map(file => file.path)};
            downloadSelection(gameId, provider.key, selection);
            onClose();
        }
    }]));

    return <Modal>
        <Modal.Backdrop isOpen onOpenChange={open => { if (!open) onClose(); }}>
            <Modal.Container size="lg">
                <Modal.Dialog>
                    <Modal.Header><Modal.Heading>Browse contents</Modal.Heading></Modal.Header>
                    <Modal.Body className="max-h-[60vh] overflow-auto">
                        {failed ? <p role="alert">Could not load game contents. Reopen the dialog to try again.</p>
                            : !nodes ? <p role="status">Loading files…</p>
                            : nodes.length === 1 ? <p>No files in this folder.</p>
                            : <TreeView data={nodes} aria-label="Game files" multiSelect togglableSelect
                                        propagateSelect propagateSelectUpwards nodeAction="check"
                                        onSelect={({treeState}) => setSelectedIds(treeState.selectedIds)}
                                        nodeRenderer={({element, isBranch, isExpanded, isSelected, isHalfSelected,
                                                           getNodeProps, handleExpand, handleSelect, level}) => {
                                            const background = isSelected ? "bg-accent/25 hover:bg-accent/45" :
                                                isHalfSelected ? "bg-accent/10 hover:bg-accent/30" : "hover:bg-default";
                                            const select = getNodeProps({onClick: handleSelect}).onClick;
                                            const Icon = isBranch ? (isExpanded ? FolderOpenIcon : FolderIcon) : FileIcon;
                                            return <div {...getNodeProps({onClick: isBranch ? handleExpand : handleSelect})}
                                                        className={`flex w-full cursor-pointer items-center gap-2 rounded px-2 py-1 focus-visible:outline ${background}`}
                                                        style={{paddingInlineStart: level * 12}}>
                                                <div className="shrink-0 p-1" onClick={event => {
                                                    event.stopPropagation();
                                                    select(event);
                                                }}>
                                                    {/* The treeitem owns keyboard focus and announces its checked state. */}
                                                    <div inert aria-hidden="true">
                                                        <Checkbox isSelected={isSelected} isIndeterminate={isHalfSelected}
                                                                  aria-label={element.name}>
                                                            <Checkbox.Content>
                                                                <Checkbox.Control><Checkbox.Indicator/></Checkbox.Control>
                                                            </Checkbox.Content>
                                                        </Checkbox>
                                                    </div>
                                                </div>
                                                <Icon className="shrink-0" aria-hidden="true"/>
                                                <span className="min-w-0 break-all">{element.name}</span>
                                                <span className="ms-auto shrink-0 text-sm tabular-nums text-muted">
                                                    {humanFileSize(nodes[Number(element.id)].metadata.size)}
                                                </span>
                                            </div>;
                                        }}/>}
                    </Modal.Body>
                    <Modal.Footer className="flex-wrap">
                        <span role="status" className="me-auto self-center text-sm tabular-nums">
                            Selected size: {humanFileSize(selectedBytes)}
                        </span>
                        <Button variant="ghost" onPress={onClose}>Cancel</Button>
                        <ComboButton options={options} actionLabel="Download selection"
                                     preferredOptionKey="preferred-download-method"/>
                    </Modal.Footer>
                </Modal.Dialog>
            </Modal.Container>
        </Modal.Backdrop>
    </Modal>;
}
