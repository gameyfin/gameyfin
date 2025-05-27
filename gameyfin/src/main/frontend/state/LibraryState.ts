import {Subscription} from "@vaadin/hilla-frontend";
import {proxy} from "valtio/index";
import {LibraryEndpoint} from "Frontend/generated/endpoints";
import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryDto";
import LibraryEvent from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryEvent";
import {handleLibraryDeletion} from "./ScanState";

type LibraryState = {
    subscription?: Subscription<LibraryEvent[]>;
    isLoaded: boolean;
    state: Record<number, LibraryDto>;
    libraries: LibraryDto[];
    sorted: LibraryDto[];
};

export const libraryState = proxy<LibraryState>({
    get isLoaded() {
        return this.subscription != null;
    },
    state: {},
    get libraries() {
        return Object.values<LibraryDto>(this.state);
    },
    get sorted() {
        return Object.values<LibraryDto>(this.state).sort((a, b) => {
            if (a.name === undefined || b.name === undefined) return 0;
            return a.name.localeCompare(b.name);
        });
    }
});

/** Subscribe to and process state updates from backend **/
export async function initializeLibraryState() {
    if (libraryState.isLoaded) return libraryState;

    // Fetch initial library list
    const initialEntries = await LibraryEndpoint.getAll();
    initialEntries.forEach((library: LibraryDto) => {
        libraryState.state[library.id] = library;
    });

    // Subscribe to real-time updates
    libraryState.subscription = LibraryEndpoint.subscribeToLibraryEvents().onNext((libraryEvents: LibraryEvent[]) => {
        libraryEvents.forEach((libraryEvent: LibraryEvent) => {
            switch (libraryEvent.type) {
                case "created":
                case "updated":
                    //@ts-ignore
                    libraryState.state[libraryEvent.library.id] = libraryEvent.library;
                    break;
                case "deleted":
                    //@ts-ignore
                    handleLibraryDeletion(libraryEvent.libraryId);
                    //@ts-ignore
                    delete libraryState.state[libraryEvent.libraryId];
                    break;
            }
        })
    });

    return libraryState;
}