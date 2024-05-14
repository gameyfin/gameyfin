import {useRouteMetadata} from 'Frontend/util/routing.js';
import {useEffect} from 'react';
import ProfileMenu from "Frontend/components/ProfileMenu";
import {Outlet} from "react-router-dom";
import {Card} from "@nextui-org/react";

export default function MainLayout() {
    const currentTitle = `Gameyfin - ${useRouteMetadata()?.title}` ?? 'Gameyfin';
    useEffect(() => {
        document.title = currentTitle;
    }, [currentTitle]);

    return (
        <>
            <Card className="sticky top-0 z-10 h-max max-w-full rounded-none px-4 py-2">
                <div className="flex items-center justify-end text-blue-gray-900">
                    <img
                        className="absolute w-full content-center h-8"
                        src="/images/Logo.svg"
                        alt="Gameyfin"
                    />
                    <ProfileMenu/>
                </div>
            </Card>

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
