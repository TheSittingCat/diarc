/**
 * @author Lucien Bao
 * @version 0.9
 * @date 3 June 2024
 * FileBrowser. Subcomponent which renders a tree view of ASL files.
 */

import React from "react";

import { faFolderClosed, faFolderOpen, faFile } from "@fortawesome/free-solid-svg-icons";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

import TreeView, { flattenTree } from "react-accessible-treeview";

import "./FileBrowser.css";

const FileBrowser = ({ fileTree, sendMessage }) => {
    const getFileOnSelect = (e) => {
        const element = e.element;
        const name: string = element.name;
        if (name.length < 5 || name.slice(-4) !== ".asl")
            return;

        sendMessage(JSON.stringify({ fileId: element.id }));
    }

    return (
        <TreeView
            data={flattenTree(fileTree)}
            className="basic"
            onNodeSelect={getFileOnSelect}
            nodeRenderer={
                ({ element, getNodeProps, level, isBranch, isExpanded }) => {
                    return (
                        <div
                            {...getNodeProps()}
                            style={{ paddingLeft: 20 * level - 15 }}
                        >
                            {isBranch ?
                                isExpanded ?
                                    <FontAwesomeIcon icon={faFolderOpen}
                                        color="#50b3ff" />
                                    : <FontAwesomeIcon icon={faFolderClosed}
                                        color="#50b3ff" />
                                : <FontAwesomeIcon icon={faFile}
                                    color="#e8be00" />
                            }
                            {" " + element.name}
                        </div>
                    )
                }
            }
        />
    );
}

export default FileBrowser;