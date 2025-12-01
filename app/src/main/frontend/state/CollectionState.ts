import {Subscription} from "@vaadin/hilla-frontend";
import {proxy} from "valtio/index";
import {CollectionEndpoint} from "Frontend/generated/endpoints";
import CollectionDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionDto";
import CollectionEvent from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionEvent";

type CollectionState = {
    subscription?: Subscription<CollectionEvent[]>;
    isLoaded: boolean;
    state: Record<number, CollectionDto>;
    collections: CollectionDto[];
    sorted: CollectionDto[];
};

export const collectionState = proxy<CollectionState>({
    get isLoaded() {
        return this.subscription != null;
    },
    state: {},
    get collections() {
        return Object.values<CollectionDto>(this.state);
    },
    get sorted() {
        return Object.values<CollectionDto>(this.state).sort((a: any, b: any) => {
            const orderA = a.metadata?.displayOrder ?? -1;
            const orderB = b.metadata?.displayOrder ?? -1;

            // Handle -1 as "end of list"
            const effectiveOrderA = orderA === -1 ? Number.MAX_SAFE_INTEGER : orderA;
            const effectiveOrderB = orderB === -1 ? Number.MAX_SAFE_INTEGER : orderB;

            const orderDiff = effectiveOrderA - effectiveOrderB;
            if (orderDiff !== 0) {
                return orderDiff;
            }

            // Fallback to creation date (newer first)
            return new Date(a.createdAt!).getTime() - new Date(b.createdAt!).getTime();
        });
    }
});

/** Subscribe to and process state updates from backend **/
export async function initializeCollectionState() {
    if (collectionState.isLoaded) return;

    // Fetch initial collection list
    const initialEntries = await CollectionEndpoint.getAll();
    initialEntries.forEach((collection: CollectionDto) => {
        collectionState.state[collection.id] = collection;
    });

    // Subscribe to real-time updates
    collectionState.subscription = CollectionEndpoint.subscribeToCollectionEvents().onNext((collectionEvents: CollectionEvent[]) => {
        collectionEvents.forEach((collectionEvent: CollectionEvent) => {
            console.log('CollectionState - received event:', collectionEvent.type, collectionEvent);
            switch (collectionEvent.type) {
                case "created":
                    //@ts-ignore
                    console.log('CollectionState - creating collection:', collectionEvent.collection.id, 'gameIds:', collectionEvent.collection.gameIds);
                    collectionState.state[collectionEvent.collection.id] = collectionEvent.collection;
                    break;
                case "updated":
                    //@ts-ignore
                    console.log('CollectionState - updating collection:', collectionEvent.collection.id, 'gameIds:', collectionEvent.collection.gameIds);
                    const updatedCollection = collectionEvent.collection;
                    const existingCollection = collectionState.state[updatedCollection.id];

                    if (existingCollection) {
                        // Update properties individually to ensure reactivity
                        Object.assign(existingCollection, updatedCollection);
                    } else {
                        // If collection doesn't exist, create it
                        collectionState.state[updatedCollection.id] = updatedCollection;
                    }
                    break;
                case "deleted":
                    //@ts-ignore
                    delete collectionState.state[collectionEvent.collectionId];
                    break;
            }
        })
    });
}

