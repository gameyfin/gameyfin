import {useRouteMetadata} from 'Frontend/util/routing.js';
import {useEffect} from 'react';
import ProfileMenu from "Frontend/components/ProfileMenu";
import {Outlet} from "react-router-dom";
import {Navbar, NavbarBrand, NavbarContent, NavbarItem} from "@nextui-org/react";
import GameyfinLogo from "Frontend/components/theming/GameyfinLogo";

export default function MainLayout() {
    const currentTitle = `Gameyfin - ${useRouteMetadata()?.title}` ?? 'Gameyfin';
    useEffect(() => {
        document.title = currentTitle;
    }, [currentTitle]);

    return (
        <>
            <Navbar maxWidth="2xl" className="shadow">
                <NavbarBrand>
                    <GameyfinLogo className="h-10 fill-foreground"/>
                </NavbarBrand>
                <NavbarContent justify="end">
                    <NavbarItem>
                        <ProfileMenu/>
                    </NavbarItem>
                </NavbarContent>
            </Navbar>

            <Outlet/>
        </>
    );
}

/*<footer
    className="flex flex-row w-full items-center justify-between px-10 py-4">
    <Typography color="blue-gray">
        Gameyfin v{packageJson.version}
    </Typography>
    <Typography color="blue-gray">
        &copy; {(new Date()).getFullYear()} <a
        href="https://github.com/gameyfin/gameyfin/graphs/contributors" target="_blank">Gameyfin
        contributors</a>
    </Typography>
</footer>*/
