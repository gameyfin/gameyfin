import {useEffect, useState} from 'react';
import ProfileMenu from "Frontend/components/ProfileMenu";
import {Button, Link, Separator, Tooltip} from "@heroui/react";
import GameyfinLogo from "Frontend/components/theming/GameyfinLogo";
import * as PackageJson from "../../../../package.json";
import {Outlet, useLocation, useNavigate} from "react-router";
import {useAuth} from "Frontend/util/auth";
import {
    ArrowLeftIcon,
    DiceSixIcon,
    DiscIcon,
    HeartIcon,
    HouseIcon,
    ListMagnifyingGlassIcon,
    SignInIcon,
} from "@phosphor-icons/react";
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
        effectInterval: 10000,
    };

    async function loadUserTheme() {
        let syncedTheme = await userPreferenceService.get("preferred-theme");
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

            <header className="2xl:px-[12.5%]">
                <nav className="flex flex-wrap items-center justify-between gap-4 px-4 py-4">
                    <div className="flex items-center gap-2">
                        {isHomePage ? <GameyfinLogo className="h-10 fill-foreground"/> :
                            <div className="flex flex-row gap-2">
                                <Button isIconOnly onPress={() => history.back()} variant="tertiary">
                                    <ArrowLeftIcon size={26} weight="bold"/>
                                </Button>
                                <Button isIconOnly onPress={() => navigate("/")} variant="tertiary">
                                    <HouseIcon size={26} weight="fill"/>
                                </Button>
                            </div>
                        }
                    </div>

                    {!isSearchPage && (
                        <div className="flex flex-1 items-center justify-center gap-2 max-w-96 min-w-72 ml-auto">
                            <Tooltip>
                                <Tooltip.Trigger>
                                    <Button
                                        isIconOnly
                                        variant="tertiary"
                                        onPress={() => navigate("/game/" + getRandomGameId())}
                                        isDisabled={gameState.games.length === 0}
                                    >
                                        <DiceSixIcon/>
                                    </Button>
                                </Tooltip.Trigger>
                                <Tooltip.Content placement="bottom">I'm feeling lucky</Tooltip.Content>
                            </Tooltip>
                            <SearchBar/>
                            <Tooltip>
                                <Tooltip.Trigger>
                                    <Button isIconOnly variant="tertiary" onPress={() => navigate("/search")}>
                                        <ListMagnifyingGlassIcon/>
                                    </Button>
                                </Tooltip.Trigger>
                                <Tooltip.Content placement="bottom">Advanced search</Tooltip.Content>
                            </Tooltip>
                        </div>
                    )}

                    <ul className="flex items-center gap-2 ml-auto">
                        <li>
                            <Tooltip>
                                <Tooltip.Trigger>
                                    <Button
                                        variant="primary"
                                        isDisabled={location.pathname.startsWith("/requests")}
                                        onPress={() => navigate("/requests")}
                                    >
                                        <DiscIcon weight="fill"/>
                                        Requests
                                    </Button>
                                </Tooltip.Trigger>
                                <Tooltip.Content placement="bottom">Request a game</Tooltip.Content>
                            </Tooltip>
                        </li>
                        {isAdmin(auth) && (
                            <li>
                                <Tooltip>
                                    <Tooltip.Trigger>
                                        <div>
                                            <ScanProgressPopover/>
                                        </div>
                                    </Tooltip.Trigger>
                                    <Tooltip.Content placement="bottom">View library scan results</Tooltip.Content>
                                </Tooltip>
                            </li>
                        )}
                        {auth.state.user && (
                            <li>
                                <ProfileMenu/>
                            </li>
                        )}
                        {!auth.state.user && (
                            <li>
                                <Tooltip>
                                    <Tooltip.Trigger>
                                        <Button
                                            variant="primary"
                                            isIconOnly
                                            className="gradient-primary rounded-full"
                                            onPress={() => window.location.href = "/loginredirect"}
                                        >
                                            <SignInIcon className="text-background/80"/>
                                        </Button>
                                    </Tooltip.Trigger>
                                    <Tooltip.Content placement="bottom">Sign in to your account</Tooltip.Content>
                                </Tooltip>
                            </li>
                        )}
                    </ul>
                </nav>
            </header>

            <div className="flex flex-col grow 2xl:px-[12.5%] overflow-x-hidden mt-4 px-4 2xl:mt-4">
                <Outlet/>
            </div>

            <Separator className="mt-8"/>
            <div className="flex flex-col w-full 2xl:px-[12.5%] px-4">
                <footer className="flex flex-row items-center justify-between py-4">
                    <p>Gameyfin {PackageJson.version}</p>
                    <p className="flex flex-row gap-1 items-baseline">
                        Made with
                        <HeartIcon size={16} weight="fill" className="text-accent" onClick={easterEgg}/>
                        by
                        <Link href="https://github.com/grimsi" target="_blank" rel="noopener noreferrer" className="underline">
                            grimsi
                        </Link> and
                        <Link
                            href="https://github.com/gameyfin/gameyfin/graphs/contributors"
                            target="_blank"
                            rel="noopener noreferrer"
                            className="underline"
                        >
                            contributors
                        </Link>
                    </p>
                </footer>
            </div>
        </div>
    );
}
