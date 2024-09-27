import {useRouteMetadata} from 'Frontend/util/routing.js';
import {useEffect} from 'react';
import ProfileMenu from "Frontend/components/ProfileMenu";
import {Divider, Link, Navbar, NavbarBrand, NavbarContent, NavbarItem} from "@nextui-org/react";
import GameyfinLogo from "Frontend/components/theming/GameyfinLogo";
import * as PackageJson from "../../../../package.json";
import {Outlet, useNavigate} from "react-router-dom";
import {useAuth} from "Frontend/util/auth";

export default function MainLayout() {
    const navigate = useNavigate();
    const auth = useAuth();
    const routeMetadata = useRouteMetadata();

    useEffect(() => {
        let newTitle = `Gameyfin - ${routeMetadata?.title}` ?? 'Gameyfin';
        window.addEventListener('popstate', () => document.title = newTitle);
    }, []);

    return (
        <div className="flex flex-col min-h-svh">
            <div className="flex flex-col flex-grow w-full 2xl:w-3/4 m-auto">
                <Navbar maxWidth="full">
                    <NavbarBrand as="button" onClick={() => navigate('/')}>
                        <GameyfinLogo className="h-10 fill-foreground"/>
                    </NavbarBrand>
                    <NavbarContent justify="end">
                        {auth.state.user?.emailConfirmed === false ?
                            <NavbarItem>
                                <small className="text-warning">Please confirm your email</small>
                            </NavbarItem>
                            :
                            ""
                        }
                        <NavbarItem>
                            <ProfileMenu/>
                        </NavbarItem>
                    </NavbarContent>
                </Navbar>

                <div className="w-full overflow-hidden ml-2 pr-8 mt-4">
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
