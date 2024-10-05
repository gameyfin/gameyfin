import {useRouteMetadata} from 'Frontend/util/routing.js';
import {useEffect, useState} from 'react';
import ProfileMenu from "Frontend/components/ProfileMenu";
import {Divider, Link, Navbar, NavbarBrand, NavbarContent, NavbarItem} from "@nextui-org/react";
import GameyfinLogo from "Frontend/components/theming/GameyfinLogo";
import * as PackageJson from "../../../../package.json";
import {Outlet, useNavigate} from "react-router-dom";
import {useAuth} from "Frontend/util/auth";
import {Heart} from "@phosphor-icons/react";
import Confetti, {ConfettiProps} from "react-confetti-boom";
import {UserPreferencesEndpoint} from "Frontend/generated/endpoints";
import {useTheme} from "next-themes";

export default function MainLayout() {
    const navigate = useNavigate();
    const auth = useAuth();
    const routeMetadata = useRouteMetadata();
    const {setTheme} = useTheme();
    const [isExploding, setIsExploding] = useState(false);

    useEffect(() => {
        let newTitle = `Gameyfin - ${routeMetadata?.title}` ?? 'Gameyfin';
        window.addEventListener('popstate', () => document.title = newTitle);
        loadUserTheme().catch(console.error);
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
        let theme = localStorage.getItem('theme');

        if (theme) {
            await UserPreferencesEndpoint.set("preferred-theme", theme);
        } else {
            let preferredTheme = await UserPreferencesEndpoint.get("preferred-theme");
            if (preferredTheme) {
                setTheme(preferredTheme);
            }
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
        <div className="flex flex-col min-h-svh">
            {isExploding ? <Confetti {...confettiProps}/> : <></>}
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
            <div className="flex flex-col w-full 2xl:w-3/4 m-auto">
                <footer className="flex flex-row items-center justify-between py-4 px-12">
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