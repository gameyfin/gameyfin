import {navigationVisibility} from "./navigationVisibility";
import {createPortal} from "react-dom";
import {RefCallback, useState} from "react";
import {Button, Link} from "@heroui/react";
import {NavLink, useLocation, useNavigate} from "react-router";
import {ArrowLeftIcon, HouseIcon, MagnifyingGlassIcon, ShootingStarIcon, ListIcon, DiceSixIcon, ListChecksIcon} from "@phosphor-icons/react";
import GameyfinLogo from "Frontend/components/theming/GameyfinLogo";
import SearchBar from "Frontend/components/general/SearchBar";
import {useProfileActions} from "./useProfileActions";
import NavigationDrawer from "./NavigationDrawer";
import {useAuth} from "Frontend/util/auth";
import {isAdmin} from "Frontend/util/utils";

export default function CompactNavigation({randomGame, hasGames, searchValue, onSearchChange, sectionMenuRef}: {sectionMenuRef: RefCallback<HTMLDivElement>; randomGame: () => void; hasGames: boolean; searchValue: string; onSearchChange: (value: string) => void}) {
    const location = useLocation();
    const navigate = useNavigate();
    const auth = useAuth();
    const actions = useProfileActions();
    const visible = navigationVisibility(!!auth.state.user, !!isAdmin(auth));
    const [open, setOpen] = useState(false);
    const home = location.pathname === "/";
    const destinations = [
        {href: "/", label: "Home", icon: <HouseIcon weight="fill"/>},
        {href: "/search", label: "Search", icon: <MagnifyingGlassIcon weight="fill"/>},
        {href: "/requests", label: "Requests", icon: <ShootingStarIcon weight="fill"/>},
    ];
    return <>
        <div className="xl:hidden flex flex-col gap-2 p-4 compact-header">
            <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-2 min-h-11">
                <div className="flex items-center gap-2">
                    {!home && <>
                        <Button isIconOnly variant="ghost" aria-label="Go back" onPress={() => history.back()}><ArrowLeftIcon weight="bold"/></Button>
                        <Button isIconOnly variant="ghost" aria-label="Home" onPress={() => navigate("/")}><HouseIcon weight="fill"/></Button>
                    </>}
                </div>
                <Link href="/" aria-label="Gameyfin home"><GameyfinLogo className="h-9 fill-foreground"/></Link>
                <div aria-hidden="true" className="min-h-11"/>
            </div>
            {!location.pathname.startsWith("/search") && <SearchBar inputValue={searchValue} onInputChange={onSearchChange}/>}
        </div>
        {createPortal(<div ref={sectionMenuRef} className="compact-section-menu xl:hidden"/>, document.body)}
        {createPortal(<nav aria-label="Primary navigation" className="compact-bottom-nav xl:hidden">
            {destinations.map(({href, label, icon}) => <NavLink key={href} to={href} end={href === "/"}
                className={({isActive}) => `compact-nav-item ${isActive ? "text-accent" : "text-muted"}`}>
                {icon}<span>{label}</span>
            </NavLink>)}
            <Button variant="ghost" className="compact-nav-item" aria-current={location.pathname.startsWith("/settings") || location.pathname.startsWith("/administration") ? "page" : undefined} aria-expanded={open} aria-haspopup="dialog"
                    onPress={() => setOpen(true)}><ListIcon weight="bold"/><span>More</span></Button>
        </nav>, document.body)}
        <NavigationDrawer title="More" open={open} onOpenChange={setOpen}>
            <div className="flex flex-col gap-2">
                {auth.state.user && <p className="text-muted wrap-anywhere">Signed in as {auth.state.user.username}</p>}
                {actions.map(action => <Button key={action.label} aria-current={action.path && location.pathname.startsWith(action.path) ? "page" : undefined} variant="ghost" className={`justify-start ${action.color === "danger" ? "text-danger" : ""}`}
                    onPress={() => {setOpen(false); action.onClick();}}>{action.icon}{action.label}</Button>)}
                {visible.tasks && <Button variant="ghost" className="justify-start" onPress={() => {setOpen(false); navigate("/administration/tasks");}}><ListChecksIcon/>Active tasks</Button>}
                <Button variant="ghost" className="justify-start" isDisabled={!hasGames} onPress={() => {setOpen(false); randomGame();}}><DiceSixIcon/>I'm feeling lucky</Button>
            </div>
        </NavigationDrawer>
    </>;
}
