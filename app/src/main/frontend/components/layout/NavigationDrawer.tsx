import {Drawer} from "@heroui/react";
import {ReactNode, useEffect} from "react";
import {useLocation} from "react-router";
import {COMPACT_QUERY, useMediaQuery} from "Frontend/util/useMediaQuery";

/** Shared navigation overlay; form dialogs deliberately do not use this route dismissal. */
export default function NavigationDrawer({title, open, onOpenChange, children}: {
    title: string; open: boolean; onOpenChange: (open: boolean) => void; children: ReactNode;
}) {
    const {key} = useLocation();
    const compact = useMediaQuery(COMPACT_QUERY);
    useEffect(() => {
        onOpenChange(false);
    }, [key, compact, onOpenChange]);
    return <Drawer isOpen={open} onOpenChange={onOpenChange}>
        <Drawer.Backdrop>
            <Drawer.Content placement="right">
                <Drawer.Dialog className="w-full max-w-sm">
                    <Drawer.CloseTrigger aria-label={`Close ${title}`}/>
                    <Drawer.Header><Drawer.Heading>{title}</Drawer.Heading></Drawer.Header>
                    <Drawer.Body className="pb-[max(1rem,env(safe-area-inset-bottom))]">{children}</Drawer.Body>
                </Drawer.Dialog>
            </Drawer.Content>
        </Drawer.Backdrop>
    </Drawer>;
}
