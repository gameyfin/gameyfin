import {Outlet} from "react-router-dom";
import {Icon} from "@phosphor-icons/react";
import {Listbox, ListboxItem} from "@nextui-org/react";
import {ReactElement, useState} from "react";

export type MenuItem = {
    title: string,
    url: string,
    icon: ReactElement<Icon>
}

export default function withSideMenu(menuItems: MenuItem[]) {
    return function PageWithSideMenu() {
        const [selectedItem, setSelectedItem] = useState<string>(initialSelected)

        /**
         * Remove a "/" at the start if it exists
         */
        function key(k: string): string {
            return k.replace(/^(\/)/, "")
        }

        /**
         * If the key starts with "/" assume it's an absolute link, else assume it's relative
         */
        function link(l: string): string {
            if (l.startsWith("/")) return l;
            const p = window.location.pathname
            return p.substring(0, p.lastIndexOf("/") + 1) + l;
        }

        /**
         * Match the initially selected item by current URL path
         */
        function initialSelected(): string {
            const p = window.location.pathname
            return p.substring(p.lastIndexOf("/") + 1, p.length);
        }

        return (
            <div className="flex flex-row">
                <div className="flex flex-col pr-8">
                    <Listbox className="min-w-60"
                             color="primary">
                        {menuItems.map((i) => (
                            <ListboxItem key={key(i.url)} startContent={i.icon} href={link(i.url)}
                                         onPress={() => setSelectedItem(i.url)}
                                         className={`h-12 ${key(i.url) === selectedItem ? "bg-primary" : ""}`}>
                                <p>{i.title}</p>
                            </ListboxItem>
                        ))}
                    </Listbox>
                </div>
                <div className="flex-1 overflow-auto">
                    <Outlet/>
                </div>
            </div>
        );
    }
}