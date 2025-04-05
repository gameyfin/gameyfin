import TreeView, {flattenTree, INode, NodeId} from "react-accessible-treeview";
import {File, Folder, FolderOpen, IconContext} from "@phosphor-icons/react";
import {useEffect, useState} from "react";
import {FilesystemEndpoint} from "Frontend/generated/endpoints";
import FileDto from "Frontend/generated/de/grimsi/gameyfin/core/filesystem/FileDto";
import FileType from "Frontend/generated/de/grimsi/gameyfin/core/filesystem/FileType";
import {IFlatMetadata} from "react-accessible-treeview/dist/TreeView/utils";

interface ITreeNode<M extends IFlatMetadata = IFlatMetadata> {
    id?: NodeId;
    name: string;
    isBranch?: boolean;
    children?: ITreeNode<M>[];
    metadata?: M;
}

export default function FileTreeView({setSelectedPath}: { setSelectedPath: (file: string) => void }) {
    const rootNode: INode = {
        id: "root",
        name: "",
        children: [],
        parent: null
    }

    const [fileTree, setFileTree] = useState<ITreeNode>();
    const [flattenedFileTree, setFlattenedFileTree] = useState<INode[]>([rootNode]);

    useEffect(() => {
        FilesystemEndpoint.listSubDirectories(undefined).then(
            result => {
                if (result === undefined) return;
                result = result.filter(r => r !== undefined);
                const nodes = fileDtosToTree(result as FileDto[]);
                const tree = flattenTree(nodes);
                setFileTree(nodes);
                setFlattenedFileTree(tree);
            }
        )
    }, []);

    function getAbsolutePath(node: INode, path: string = ""): string {
        if (node.parent === null) {
            return path ? `${node.name}/${path}` : node.name;
        }
        const parentNode = flattenedFileTree.find(n => n.id === node.parent);
        if (!parentNode) {
            throw new Error(`Parent node with id ${node.parent} not found`);
        }
        return getAbsolutePath(parentNode, `${node.name}/${path}`);
    }

    async function onLoadData({element}: { element: INode }) {
        const absolutePath = getAbsolutePath(element);

        let subDirectories = await FilesystemEndpoint.listSubDirectories(absolutePath);
        if (subDirectories === undefined) return;
        subDirectories = subDirectories.filter(r => r !== undefined);

        const newNodes = fileDtosToNodes(subDirectories as FileDto[]);
        const updatedTree = updateTreeWithNewNodes(fileTree!!, element.id, newNodes);

        setFileTree(updatedTree);
        setFlattenedFileTree(flattenTree(updatedTree));
        setSelectedPath(absolutePath);
    }

    function updateTreeWithNewNodes(tree: ITreeNode, nodeId: NodeId, newNodes: ITreeNode[]): ITreeNode {
        if (tree.id === nodeId) {
            return {...tree, children: newNodes};
        }

        if (tree.children) {
            return {
                ...tree,
                children: tree.children.map(child => updateTreeWithNewNodes(child, nodeId, newNodes))
            };
        }

        return tree;
    }

    function fileDtosToTree(fileDtos: FileDto[], parent: (INode | null) = null): ITreeNode {
        const nodes = fileDtosToNodes(fileDtos);

        if (parent === null) {
            return {...rootNode, children: nodes};
        }

        return {...parent, children: nodes};
    }

    function fileDtosToNodes(fileDtos: FileDto[]): ITreeNode[] {
        return fileDtos.map(fileDto => ({
            id: fileDto.hash,
            name: fileDto.name || "",
            isBranch: fileDto.type === FileType.DIRECTORY,
            children: []
        }));
    }

    return (
        <div className="flex flex-col gap-4 bg-contrast">
            <TreeView
                data={flattenedFileTree}
                aria-label="directory tree"
                onLoadData={onLoadData}
                nodeRenderer={({
                                   element,
                                   isBranch,
                                   isExpanded,
                                   isSelected,
                                   getNodeProps,
                                   level,
                               }) => (
                    <IconContext.Provider value={{size: 32, weight: "regular"}}>
                        <div {...getNodeProps()}
                             className={`
                             flex flex-row items-center gap-2
                             rounded-md cursor-pointer
                             ${isSelected ? 'bg-primary' : 'hover:bg-primary/20'}`
                             }
                             style={{paddingLeft: 10 * (level - 1)}}>
                            {isBranch ? <FolderIcon isOpen={isExpanded}/> : <FileIcon fileName={element.name}/>}
                            {element.name}
                        </div>
                    </IconContext.Provider>
                )}
            />
            <pre>{JSON.stringify(flattenedFileTree, null, 2)}</pre>
        </div>
    );
}

function FolderIcon({isOpen}: { isOpen: boolean }) {
    return isOpen ? <FolderOpen/> : <Folder/>;
}

function FileIcon({fileName}: { fileName: string }) {
    return <File/>;
}