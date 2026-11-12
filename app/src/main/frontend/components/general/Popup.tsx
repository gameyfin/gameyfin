import {Popover} from "@heroui/react";
import {ComponentProps} from "react";
import {Button} from "react-aria-components";

function PopupTrigger({className = "", ...props}: Omit<ComponentProps<typeof Button>, "className"> & {className?: string}) {
    return <Button type="button" aria-label="Show details"
                   className={`inline-flex items-center cursor-pointer rounded-sm focus-visible:outline-2 focus-visible:outline-offset-2 ${className}`}
                   {...props}/>;
}

function PopupContent({children, ...props}: ComponentProps<typeof Popover.Content>) {
    return <Popover.Content {...props}>
        <Popover.Dialog aria-label="Details">
            {children}
        </Popover.Dialog>
    </Popover.Content>;
}

/** Information revealed by mouse, touch, or keyboard activation. */
const Popup = Object.assign((props: ComponentProps<typeof Popover>) => <Popover {...props}/>, {
    Trigger: PopupTrigger,
    Content: PopupContent
});

export default Popup;
