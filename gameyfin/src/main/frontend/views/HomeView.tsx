import {useEffect, useState} from "react";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryDto";

export default function HomeView() {
    const [libraries, setLibraries] = useState<LibraryDto[]>([]);

    useEffect(() => {
        LibraryEndpoint.getAllLibraries().then(libraries => {
            setLibraries(libraries);
        });
    }, [])

    return (
        <div className="grow justify-center mt-12">
            <div className="flex flex-col items-center gap-6">
                <p>Welcome to Gameyfin!</p>
            </div>
        </div>
    );
}