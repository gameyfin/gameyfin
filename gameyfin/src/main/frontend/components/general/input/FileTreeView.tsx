import TreeView, {flattenTree} from "react-accessible-treeview";
import {File, Folder, FolderOpen, IconContext} from "@phosphor-icons/react";

const folder = {
    name: "",
    children: [
        {
            name: "src",
            children: [{name: "index.js"}, {name: "styles.css"}],
        },
        {
            name: "node_modules",
            children: [
                {
                    name: "react-accessible-treeview",
                    children: [{name: "index.js"}],
                },
                {name: "react", children: [{name: "index.js"}]},
            ],
        },
        {
            name: ".npmignore",
        },
        {
            name: "package.json",
        },
        {
            name: "webpack.config.js",
        },
    ],
};

const data = flattenTree(folder);

export default function FileTreeView({}) {
    return (
        <div className="flex flex-col gap-4">
            <TreeView
                data={data}
                aria-label="directory tree"
                nodeRenderer={({
                                   element,
                                   isBranch,
                                   isExpanded,
                                   getNodeProps,
                                   level,
                               }) => (
                    <IconContext.Provider value={{size: 32, weight: "regular"}}>
                        <div {...getNodeProps()} className="flex flex-row items-center gap-2"
                             style={{paddingLeft: 10 * (level - 1)}}>
                            {isBranch ? <FolderIcon isOpen={isExpanded}/> : <FileIcon fileName={element.name}/>}
                            {element.name}
                        </div>
                    </IconContext.Provider>
                )}
                className="w-full"
            />
            <pre>{JSON.stringify(data, null, 2)}</pre>
        </div>
    );
}

function FolderIcon({isOpen}: { isOpen: boolean }) {
    return isOpen ? <FolderOpen/> : <Folder/>;
}

function FileIcon({fileName}: { fileName: string }) {
    return <File/>;
}