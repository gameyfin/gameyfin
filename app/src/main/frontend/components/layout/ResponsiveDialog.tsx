import {ComponentProps} from "react";
import {Modal} from "@heroui/react";

/** Same dialog tree at every width: resizing never discards form state. */
export default function ResponsiveDialog({className = "", ...props}: ComponentProps<typeof Modal.Container>) {
    return <Modal.Container {...props} className={`responsive-editor ${className}`}/>;
}
