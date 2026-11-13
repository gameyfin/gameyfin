import {Button, Tooltip} from "@heroui/react";
import {redirectToLogin} from "Frontend/util/login";
import {useLocation, useNavigate} from "react-router";
import {
    ArrowLeftIcon,
    DiceSixIcon,
    HouseIcon,
    ListMagnifyingGlassIcon,
    ShootingStarIcon,
    SignInIcon
} from "@phosphor-icons/react";
import GameyfinLogo from "Frontend/components/theming/GameyfinLogo";
import SearchBar from "Frontend/components/general/SearchBar";
import ActiveTaskPopover from "Frontend/components/general/ActiveTaskPopover";
import ProfileMenu from "Frontend/components/ProfileMenu";
import NotificationPopover from "Frontend/components/general/NotificationPopover";
import {useAuth} from "Frontend/util/auth";
import {isAdmin} from "Frontend/util/utils";

export default function DesktopNavigation({randomGame, hasGames, searchValue, onSearchChange}: {
    randomGame: () => void;
    hasGames: boolean;
    searchValue: string;
    onSearchChange: (value: string) => void
}) {
    const location = useLocation();
    const navigate = useNavigate();
    const auth = useAuth();
    const isHomePage = location.pathname === "/";
    const isSearchPage = location.pathname.startsWith("/search");
    return (
        <nav aria-label="Desktop navigation"
             className="flex flex-wrap items-center justify-between gap-4 px-4 py-4 [&_button>svg]:size-5">
            <div className="flex flex-1 items-center gap-2 h-10">
                {isHomePage ? <GameyfinLogo className="h-10 fill-foreground"/> :
                    <div className="flex flex-row gap-2">
                        <Button isIconOnly aria-label="Go back" onPress={() => history.back()} variant="ghost">
                            <ArrowLeftIcon weight="bold"/>
                        </Button>
                        <Button isIconOnly aria-label="Home" onPress={() => navigate("/")} variant="ghost">
                            <HouseIcon weight="fill"/>
                        </Button>
                    </div>
                }
            </div>

            {!isSearchPage && (
                <div className="flex flex-none w-[472px] items-center justify-center gap-2">
                    <Tooltip>
                        <Tooltip.Trigger>
                            <Button
                                isIconOnly
                                aria-label="Random game"
                                variant="ghost"
                                onPress={randomGame}
                                isDisabled={!hasGames}
                            >
                                <DiceSixIcon/>
                            </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content placement="bottom">I'm feeling lucky</Tooltip.Content>
                    </Tooltip>
                    <SearchBar inputValue={searchValue} onInputChange={onSearchChange}/>
                    <Tooltip>
                        <Tooltip.Trigger>
                            <Button isIconOnly aria-label="Advanced search" variant="ghost"
                                    onPress={() => navigate("/search")}>
                                <ListMagnifyingGlassIcon/>
                            </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content placement="bottom">Advanced search</Tooltip.Content>
                    </Tooltip>
                </div>
            )}

            <ul className="flex flex-1 items-center justify-end gap-2">
                <li>
                    <Tooltip>
                        <Tooltip.Trigger>
                            <Button
                                variant="primary"
                                isDisabled={location.pathname.startsWith("/requests")}
                                onPress={() => navigate("/requests")}
                            >
                                <ShootingStarIcon weight="fill"/>
                                Requests
                            </Button>
                        </Tooltip.Trigger>
                        <Tooltip.Content placement="bottom">Request a game</Tooltip.Content>
                    </Tooltip>
                </li>
                {auth.state.user && (
                    <li>
                        <Tooltip>
                            <Tooltip.Trigger>
                                <div>
                                    <NotificationPopover/>
                                </div>
                            </Tooltip.Trigger>
                            <Tooltip.Content placement="bottom">View notifications</Tooltip.Content>
                        </Tooltip>
                    </li>
                )}
                {isAdmin(auth) && (
                    <li>
                        <Tooltip>
                            <Tooltip.Trigger>
                                <div>
                                    <ActiveTaskPopover/>
                                </div>
                            </Tooltip.Trigger>
                            <Tooltip.Content placement="bottom">View tasks</Tooltip.Content>
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
                                    aria-label="Sign in"
                                    isIconOnly
                                    className="gradient-primary rounded-full"
                                    onPress={() => redirectToLogin()}
                                >
                                    <SignInIcon className="text-accent-foreground"/>
                                </Button>
                            </Tooltip.Trigger>
                            <Tooltip.Content placement="bottom">Sign in to your account</Tooltip.Content>
                        </Tooltip>
                    </li>
                )}
            </ul>
        </nav>
    );
}
