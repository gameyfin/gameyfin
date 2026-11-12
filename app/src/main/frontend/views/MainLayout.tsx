import {CompactHeaderActionsContext} from "Frontend/components/layout/CompactHeaderActionsContext";
import DesktopNavigation from "Frontend/components/layout/DesktopNavigation";
import {COMPACT_QUERY, useMediaQuery} from "Frontend/util/useMediaQuery";
import CompactNavigation from "Frontend/components/layout/CompactNavigation";
import {useEffect, useRef, useState} from 'react';
import {Link, Separator} from "@heroui/react";
import * as PackageJson from "../../../../package.json";
import {Outlet, useNavigate} from "react-router";
import {useAuth} from "Frontend/util/auth";
import {HeartIcon} from "@phosphor-icons/react";
import Confetti, {ConfettiProps} from "react-confetti-boom";
import {useTheme} from "next-themes";
import {useUserPreferenceService} from "Frontend/util/user-preference-service";
import {useSnapshot} from "valtio/react";
import {gameState} from "Frontend/state/GameState";
import {applyAccentTheme} from "Frontend/theming/themes";
import {layoutState} from "Frontend/state/LayoutState";

export default function MainLayout() {
    const navigate = useNavigate();
    const auth = useAuth();
    const userPreferenceService = useUserPreferenceService();
    const {setTheme} = useTheme();
    const [headerActions, setHeaderActions] = useState<HTMLDivElement | null>(null);
    const [quickSearch, setQuickSearch] = useState("");
    const [isExploding, setIsExploding] = useState(false);
    const games = useSnapshot(gameState).games;
    const {stickyHeader} = useSnapshot(layoutState);
    const compact = useMediaQuery(COMPACT_QUERY);
    const randomGame = () => {
        if (games.length) navigate("/game/" + getRandomGameId());
    };
    const headerRef = useRef<HTMLElement>(null);

    useEffect(() => {
        const header = headerRef.current;
        if (!header) return;
        const observer = new ResizeObserver(() => {
            layoutState.headerHeight = header.offsetHeight;
        });
        observer.observe(header);
        return () => observer.disconnect();
    }, []);

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
            const separatorIndex = syncedTheme.lastIndexOf("-");
            const accentTheme = syncedTheme.substring(0, separatorIndex);
            const mode = syncedTheme.substring(separatorIndex + 1);
            applyAccentTheme(accentTheme);
            setTheme(mode);
        }
        const syncedSticky = await userPreferenceService.get("sticky-header");
        if (syncedSticky !== undefined) {
            layoutState.stickyHeader = syncedSticky === "true";
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
        <div className="app-shell flex flex-col min-h-dvh">
            <a href="#main-content" className="sr-only focus:not-sr-only focus:p-3">Skip to content</a>
            {isExploding ? <Confetti {...confettiProps}/> : <></>}

            <header ref={headerRef}
                    className={`2xl:px-[12.5%] ${stickyHeader ? "sm:sticky sm:top-0 sm:z-20 sm:bg-background/75 sm:backdrop-blur-md sm:shadow-sm" : ""}`}>
                {compact ? <CompactNavigation sectionMenuRef={setHeaderActions} randomGame={randomGame}
                                              hasGames={games.length > 0} searchValue={quickSearch}
                                              onSearchChange={setQuickSearch}/> :
                    <DesktopNavigation randomGame={randomGame} hasGames={games.length > 0} searchValue={quickSearch}
                                       onSearchChange={setQuickSearch}/>}
            </header>

            <main id="main-content" tabIndex={-1}
                  className="flex flex-col grow min-w-0 2xl:px-[12.5%] mt-4 px-4 2xl:mt-4">
                <CompactHeaderActionsContext.Provider value={headerActions}>
                    <Outlet/>
                </CompactHeaderActionsContext.Provider>
            </main>

            <Separator className="mt-8"/>
            <div className="flex flex-col w-full 2xl:px-[12.5%] px-4">
                <footer className="flex flex-col gap-2 sm:flex-row items-center justify-between py-4">
                    <p>Gameyfin {PackageJson.version}</p>
                    <p className="flex flex-row flex-wrap justify-center gap-1 items-baseline">
                        Made with
                        <HeartIcon size={16} weight="fill" className="text-accent" onClick={easterEgg}/>
                        by
                        <Link href="https://github.com/grimsi" target="_blank" rel="noopener noreferrer"
                              className="underline">
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
