import {createPortal} from "react-dom";
import {CompactHeaderActionsContext} from "Frontend/components/layout/CompactHeaderActionsContext";
import {COMPACT_QUERY, useMediaQuery} from "Frontend/util/useMediaQuery";
import NavigationDrawer from "Frontend/components/layout/NavigationDrawer";
import {Outlet, useLocation} from "react-router";
import {Icon, ListIcon} from "@phosphor-icons/react";
import {Button, ListBox} from "@heroui/react";
import {ReactElement, useContext, useState} from "react";
import {useSnapshot} from "valtio/react";
import {layoutState} from "Frontend/state/LayoutState";

export type MenuItem = {
    title: string,
    url: string,
    icon: ReactElement<Icon>
}

/** Matches the `mt-4` (16px) gap MainLayout puts above the page content */
const CONTENT_TOP_GAP = 16;

export default function withSideMenu(baseUrl: string, menuItems: MenuItem[]) {
    return function PageWithSideMenu() {
        const headerActions = useContext(CompactHeaderActionsContext);
        const compact = useMediaQuery(COMPACT_QUERY);
        const location = useLocation();
        const [menuOpen, setMenuOpen] = useState(false);
        const selectedItem = selectedFromPath(location.pathname);
        const {stickyHeader, headerHeight} = useSnapshot(layoutState);

        /**
         * Remove a "/" at the start if it exists
         */
        function key(k: string): string {
            return k.replace(/^(\/)/, "");
        }

        /**
         * If the key starts with "/" assume it's an absolute link, else assume it's relative
         */
        function link(l: string): string {
            if (l.startsWith("/")) return baseUrl + l;
            return baseUrl + "/" + l;
        }

        /**
         * Match the selected item by current URL path
         */
        function selectedFromPath(p: string): string {
            const idx = p.indexOf(baseUrl);
            if (idx === -1) return "";
            const afterBase = p.substring(idx + baseUrl.length);
            // Remove leading slash, then split and take the first segment
            return afterBase.replace(/^\/+/, "").split("/")[0] || "";
        }

        const menu = (
            <ListBox aria-label="Sections" className="w-full">
                {menuItems.map((i) => (
                    <ListBox.Item key={key(i.url)} id={key(i.url)} href={link(i.url)}
                                  onAction={() => setMenuOpen(false)}
                                  aria-current={key(i.url) === selectedItem ? "page" : undefined}
                                  className={`h-12 ${key(i.url) === selectedItem ? "bg-accent text-accent-foreground" : ""}`}>
                        <div className="flex flex-row gap-2 items-center">
                            {i.icon}
                            <p>{i.title}</p>
                        </div>
                    </ListBox.Item>
                ))}
            </ListBox>
        );

        return (
            <div className="flex flex-col xl:flex-row min-w-0">
                {compact && headerActions && createPortal(
                    <Button isIconOnly variant="secondary" className="min-h-11 min-w-11 shrink-0"
                            aria-label="Open section menu" aria-haspopup="dialog" aria-expanded={menuOpen}
                            onPress={() => setMenuOpen(true)}>
                        <ListIcon aria-hidden="true"/>
                    </Button>, headerActions
                )}
                {compact && <NavigationDrawer title="Sections" open={menuOpen}
                                              onOpenChange={setMenuOpen}>{menu}</NavigationDrawer>}
                <div className="hidden xl:flex flex-col w-68 shrink-0 pr-8 sticky self-start"
                     style={{top: (stickyHeader ? headerHeight : 0) + CONTENT_TOP_GAP}}>
                    {!compact && menu}

                </div>
                <div className="flex-1 min-w-0">
                    <Outlet/>
                </div>
            </div>
        );
    };
}
