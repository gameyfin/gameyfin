import {useRouteMetadata} from 'Frontend/util/routing.js';
import {useEffect, useState} from 'react';
import ProfileMenu from "Frontend/components/ProfileMenu";
import {Divider, Link, Navbar, NavbarBrand, NavbarContent, NavbarItem} from "@heroui/react";
import GameyfinLogo from "Frontend/components/theming/GameyfinLogo";
import * as PackageJson from "../../../../package.json";
import {Outlet, useNavigate} from "react-router";
import {useAuth} from "Frontend/util/auth";
import {Heart} from "@phosphor-icons/react";
import Confetti, {ConfettiProps} from "react-confetti-boom";
import {useTheme} from "next-themes";
import {UserPreferenceService} from "Frontend/util/user-preference-service";
import SearchBar from "Frontend/components/general/SearchBar";

export default function MainLayout() {
    const navigate = useNavigate();
    const auth = useAuth();
    const routeMetadata = useRouteMetadata();
    const {setTheme} = useTheme();
    const [isExploding, setIsExploding] = useState(false);

    useEffect(() => {
        let newTitle = `Gameyfin - ${routeMetadata?.title}`;
        window.addEventListener('popstate', () => document.title = newTitle);

        UserPreferenceService.sync()
            .then(() => loadUserTheme().catch(console.error))
            .catch(console.error);
    }, []);

    const confettiProps: ConfettiProps = {
        mode: 'boom',
        x: 0.5,
        y: 1,
        particleCount: 1000,
        spreadDeg: 90,
        launchSpeed: 4,
        effectInterval: 10000
    }

    async function loadUserTheme() {
        let syncedTheme = await UserPreferenceService.get("preferred-theme")
        if (syncedTheme !== undefined) {
            setTheme(syncedTheme);
        }
    }

    function easterEgg() {
        if (isExploding) return;
        setIsExploding(true);
        if (confettiProps.mode === "boom") {
            setTimeout(() => setIsExploding(false), confettiProps.effectInterval);
        }
    }

    return (
        <div className="flex flex-col min-h-screen">
            {isExploding ? <Confetti {...confettiProps}/> : <></>}

            <Navbar maxWidth="full" className="2xl:px-[12.5%]">
                <NavbarBrand>
                    <div className="cursor-pointer" onClick={() => navigate('/')}>
                        <GameyfinLogo className="h-10 fill-foreground"/>
                    </div>
                </NavbarBrand>
                <NavbarContent justify="center" className="flex-1 max-w-96">
                    <SearchBar/>
                </NavbarContent>
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

            <div className="flex flex-col flex-grow 2xl:px-[12.5%] overflow-x-hidden mt-4">
                <Outlet/>
            </div>

            <Divider className="mt-8"/>
            <div className="flex flex-col w-full 2xl:px-[12.5%]">
                <footer className="flex flex-row items-center justify-between py-4">
                    <p>Gameyfin {PackageJson.version}</p>
                    <p className="flex flex-row gap-1 items-baseline">
                        Made with
                        <Heart size={16} weight="fill" className="text-primary" onClick={easterEgg}/>
                        by
                        <Link href="https://github.com/grimsi" target="_blank">grimsi</Link> and
                        <Link href="https://github.com/gameyfin/gameyfin/graphs/contributors" target="_blank">
                            contributors
                        </Link>
                    </p>
                </footer>
            </div>
        </div>
    );
}