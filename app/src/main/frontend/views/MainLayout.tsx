import {useEffect, useState} from 'react';
import ProfileMenu from "Frontend/components/ProfileMenu";
import {Button, Divider, Link, Navbar, NavbarBrand, NavbarContent, NavbarItem, Tooltip} from "@heroui/react";
import GameyfinLogo from "Frontend/components/theming/GameyfinLogo";
import * as PackageJson from "../../../../package.json";
import {Outlet, useLocation, useNavigate} from "react-router";
import {useAuth} from "Frontend/util/auth";
import {ArrowLeft, DiceSix, Disc, Heart, House, ListMagnifyingGlass, SignIn} from "@phosphor-icons/react";
import Confetti, {ConfettiProps} from "react-confetti-boom";
import {useTheme} from "next-themes";
import {useUserPreferenceService} from "Frontend/util/user-preference-service";
import SearchBar from "Frontend/components/general/SearchBar";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import ScanProgressPopover from "Frontend/components/general/ScanProgressPopover";
import {isAdmin} from "Frontend/util/utils";

export default function MainLayout() {
    const navigate = useNavigate();
    const location = useLocation();
    const auth = useAuth();
    const userPreferenceService = useUserPreferenceService();
    const {setTheme} = useTheme();
    const isSearchPage = location.pathname.startsWith("/search");
    const isHomePage = location.pathname === "/";
    const [isExploding, setIsExploding] = useState(false);
    const games = useSnapshot(gameState).games;

    useEffect(() => {
        userPreferenceService.sync()
            .then(() => loadUserTheme().catch(console.error))
            .catch(console.error);
    }, [auth.state.user]);

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
        let syncedTheme = await userPreferenceService.get("preferred-theme")
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

    function getRandomGameId() {
        return games[Math.floor(Math.random() * games.length)].id;
    }

    return (
        <div className="flex flex-col min-h-screen">
            {isExploding ? <Confetti {...confettiProps}/> : <></>}

            <Navbar maxWidth="full" className="2xl:px-[12.5%]">
                <NavbarBrand>
                    {isHomePage ? <GameyfinLogo className="h-10 fill-foreground"/> :
                        <div className="flex flex-row gap-2">
                            <Button isIconOnly onPress={() => history.back()} variant="light">
                                <ArrowLeft size={26} weight="bold"/>
                            </Button>
                            <Button isIconOnly onPress={() => navigate("/")} variant="light">
                                <House size={26} weight="fill"/>
                            </Button>
                        </div>
                    }
                </NavbarBrand>
                {!isSearchPage && <NavbarContent justify="center" className="flex-1 max-w-96">
                    <Tooltip content="I'm feeling lucky" placement="bottom">
                        <Button isIconOnly variant="light"
                                onPress={() => navigate("/game/" + getRandomGameId())}
                                isDisabled={gameState.games.length === 0}>
                            <DiceSix/>
                        </Button>
                    </Tooltip>
                    <SearchBar/>
                    <Tooltip content="Advanced search" placement="bottom">
                        <Button isIconOnly variant="light" onPress={() => navigate("/search")}>
                            <ListMagnifyingGlass/>
                        </Button>
                    </Tooltip>
                </NavbarContent>}
                <NavbarContent justify="end" className="items-center">
                    {auth.state.user &&
                        <NavbarItem>
                            <Tooltip content="Request a game" placement="bottom">
                                <Button color="primary"
                                        isDisabled={window.location.pathname.startsWith("/requests")}
                                        onPress={() => navigate("/requests")}
                                        startContent={<Disc weight="fill"/>}>
                                    Requests
                                </Button>
                            </Tooltip>
                        </NavbarItem>
                    }
                    {isAdmin(auth) &&
                        <NavbarItem>
                            <Tooltip content="View library scan results" placement="bottom">
                                <div>
                                    <ScanProgressPopover/>
                                </div>
                            </Tooltip>
                        </NavbarItem>
                    }
                    {auth.state.user &&
                        <NavbarItem>
                            <ProfileMenu/>
                        </NavbarItem>
                    }
                    {!auth.state.user &&
                        <NavbarItem>
                            <Tooltip content="Sign in to your account" placement="bottom">
                                <Button color="primary"
                                        radius="full"
                                        isIconOnly
                                        className="gradient-primary"
                                    /* This is hacky but works since "/loginredirect" is not configured and returns 401 for not logged-in users.
                                        This triggers Hilla to redirect to the correct login page (integrated or SSO) automatically.
                                        Otherwise, SSO login would not be possible if we redirect to "/login" directly */
                                        onPress={() => window.location.href = "/loginredirect"}>
                                    <SignIn fill="text-background/80"/>
                                </Button>
                            </Tooltip>
                        </NavbarItem>
                    }
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