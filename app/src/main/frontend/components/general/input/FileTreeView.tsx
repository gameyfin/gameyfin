import TreeView, {flattenTree, INode, NodeId} from "react-accessible-treeview";
import {
    FileIcon as PhFileIcon,
    FolderIcon as PhFolderIcon,
    FolderOpenIcon as PhFolderOpenIcon,
    IconContext
} from "@phosphor-icons/react";
import {useEffect, useState} from "react";
import {FilesystemEndpoint} from "Frontend/generated/endpoints";
import FileDto from "Frontend/generated/org/gameyfin/app/core/filesystem/FileDto";
import FileType from "Frontend/generated/org/gameyfin/app/core/filesystem/FileType";
import {IFlatMetadata} from "react-accessible-treeview/dist/TreeView/utils";
import OperatingSystemType from "Frontend/generated/org/gameyfin/app/core/filesystem/OperatingSystemType";

interface ITreeNode<M extends IFlatMetadata = IFlatMetadata> {
    id?: NodeId;
    name: string;
    isBranch?: boolean;
    children?: ITreeNode<M>[];
    metadata?: M;
}

export default function FileTreeView({onPathChange}: { onPathChange: (file: string) => void }) {
    const rootNode: INode = {
        id: "root",
        name: "",
        children: [],
        parent: null
    }

    const [hostOSType, setHostOSType] = useState<OperatingSystemType>();
    const [fileTree, setFileTree] = useState<ITreeNode>();
    const [flattenedFileTree, setFlattenedFileTree] = useState<INode[]>([rootNode]);

    useEffect(() => {
        FilesystemEndpoint.getHostOperatingSystem().then((response) => {
            setHostOSType(response);
        })

        FilesystemEndpoint.listSubDirectories("").then(
            result => {
                if (result === undefined) return;
                const nodes = fileDtosToTree(result as FileDto[]);
                const tree = flattenTree(nodes);
                setFileTree(nodes);
                setFlattenedFileTree(tree);
            }
        )
    }, []);

    function getAbsolutePath(node: INode, path: string = ""): string {
        let pathSeparator = "/";

        if (hostOSType === OperatingSystemType.WINDOWS) {
            pathSeparator = "\\";
            if (path.startsWith(pathSeparator)) path = path.substring(1);
        }

        path = path.replace(`${pathSeparator}${pathSeparator}`, pathSeparator);

        if (node.parent === null) {
            if (hostOSType === OperatingSystemType.WINDOWS) return path;
            return `${pathSeparator}${path}`;
        }

        const parentNode = flattenedFileTree.find(n => n.id === node.parent);
        if (!parentNode) {
            throw new Error(`Parent node with id ${node.parent} not found`);
        }
        return getAbsolutePath(parentNode, `${node.name}${pathSeparator}${path}`);
    }

    async function onLoadData({element}: { element: INode }) {
        const absolutePath = getAbsolutePath(element);

        let subDirectories = await FilesystemEndpoint.listSubDirectories(absolutePath);
        if (subDirectories === undefined) return;

        const newNodes = fileDtosToNodes(subDirectories as FileDto[]);
        const updatedTree = updateTreeWithNewNodes(fileTree!, element.id, newNodes);

        setFileTree(updatedTree);
        setFlattenedFileTree(flattenTree(updatedTree));
        onPathChange(absolutePath);
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
        <div className="flex flex-col flex-1 w-full gap-4 overflow-hidden">
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
                             flex flex-row items-center gap-2 w-full
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
        </div>
    );
}

function FolderIcon({isOpen}: { isOpen: boolean }) {
    return isOpen ? <PhFolderOpenIcon/> : <PhFolderIcon/>;
}

function FileIcon({fileName}: { fileName: string }) {
    return <PhFileIcon/>;
}