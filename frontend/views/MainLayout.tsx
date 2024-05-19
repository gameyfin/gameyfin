import {useRouteMetadata} from 'Frontend/util/routing.js';
import {useEffect} from 'react';
import ProfileMenu from "Frontend/components/ProfileMenu";
import {Divider, Link, Navbar, NavbarBrand, NavbarContent, NavbarItem} from "@nextui-org/react";
import GameyfinLogo from "Frontend/components/theming/GameyfinLogo";
import * as PackageJson from "../../package.json";
import {Outlet, useNavigate} from "react-router-dom";

export default function MainLayout() {
    const currentTitle = `Gameyfin - ${useRouteMetadata()?.title}` ?? 'Gameyfin';
    useEffect(() => {
        document.title = currentTitle;
    }, [currentTitle]);
    const navigate = useNavigate();

    return (
        <div className="flex flex-col min-h-svh">
            <Navbar maxWidth="2xl" className="shadow">
                <NavbarBrand as="button" onClick={() => navigate('/')}>
                    <GameyfinLogo className="h-10 fill-foreground"/>
                </NavbarBrand>
                <NavbarContent justify="end">
                    <NavbarItem>
                        <ProfileMenu/>
                    </NavbarItem>
                </NavbarContent>
            </Navbar>

            <div className="flex-1">
                <div className="flex-row relative m-auto max-w-[1536px] align-self-center px-2 pt-4">
                    <Outlet/>
                </div>
            </div>

            <Divider/>

            <footer className="flex flex-row items-center justify-between py-4 px-12">
                <p>Gameyfin {PackageJson.version}</p>
                <p>
                    &copy; {(new Date()).getFullYear()}&ensp;
                    <Link href="https://github.com/gameyfin/gameyfin/graphs/contributors" target="_blank">
                        Gameyfin contributors
                    </Link>
                </p>
            </footer>
        </div>
    );
}

/**/
