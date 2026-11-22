import {ActionOnLostSubscription, Subscription} from "@vaadin/hilla-frontend";
import {proxy} from "valtio";
import {CollectionEndpoint, GameSocialEndpoint} from "Frontend/generated/endpoints";
import CollectionDto from "Frontend/generated/org/gameyfin/app/collections/dto/CollectionDto";
import SocialSettings from "Frontend/generated/org/gameyfin/app/social/SocialSettings";

type CollectionState = {
    state: Record<number, CollectionDto>;
    isLoaded: boolean;
    settings?: SocialSettings;
    sorted: CollectionDto[];
};

export const collectionState = proxy<CollectionState>({
    state: {},
    isLoaded: false,
    settings: undefined,
    get sorted() {
        return Object.values<CollectionDto>(this.state).sort((a, b) => {
            const orderA = a.metadata.displayOrder === -1 ? Infinity : a.metadata.displayOrder;
            const orderB = b.metadata.displayOrder === -1 ? Infinity : b.metadata.displayOrder;
            return orderA - orderB || new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
        });
    }
});

let collections: Subscription<CollectionDto[]> | undefined;
let settings: Subscription<SocialSettings> | undefined;
let generation = 0;

function clearCollections() {
    collectionState.state = {};
    collectionState.isLoaded = false;
}

export function resetCollectionState() {
    generation++;
    collections?.cancel();
    settings?.cancel();
    collections = undefined;
    settings = undefined;
    clearCollections();
    collectionState.settings = undefined;
}

export function initializeCollectionState(member: boolean) {
    resetCollectionState();
    const current = generation;
    collections = CollectionEndpoint.subscribeToCollections()
        .onNext(items => {
            if (current !== generation) return;
            collectionState.state = Object.fromEntries(items.map(item => [item.id, item]));
            collectionState.isLoaded = true;
        })
        .onComplete(() => {
            if (current === generation) clearCollections();
        })
        .onConnectionStateChange(event => {
            if (current === generation && event.detail.state !== "connected") clearCollections();
        })
        .onSubscriptionLost(() => {
            if (current !== generation) return ActionOnLostSubscription.REMOVE;
            clearCollections();
            return ActionOnLostSubscription.RESUBSCRIBE;
        });
    settings = GameSocialEndpoint.subscribeSettings()
        .onNext(value => {
            if (current === generation) collectionState.settings = value;
        })
        .onSubscriptionLost(() => {
            if (current !== generation) return ActionOnLostSubscription.REMOVE;
            collectionState.settings = undefined;
            return ActionOnLostSubscription.RESUBSCRIBE;
        });
    if (member) void CollectionEndpoint.ensureFavourites().catch(console.error);
}
