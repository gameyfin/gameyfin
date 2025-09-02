import {Subscription} from "@vaadin/hilla-frontend";
import {proxy} from "valtio/index";
import {GameRequestEndpoint} from "Frontend/generated/endpoints";
import GameRequestEvent from "Frontend/generated/org/gameyfin/app/requests/dto/GameRequestEvent";
import GameRequestDto from "Frontend/generated/org/gameyfin/app/requests/dto/GameRequestDto";

type GameRequestState = {
    subscription?: Subscription<GameRequestEvent[]>;
    isLoaded: boolean;
    state: Record<number, GameRequestDto>;
    gameRequests: GameRequestDto[];
};

export const gameRequestState = proxy<GameRequestState>({
    get isLoaded() {
        return this.subscription != null;
    },
    state: {},
    get gameRequests() {
        return Object.values<GameRequestDto>(this.state);
    }
});

/** Subscribe to and process state updates from backend **/
export async function initializeGameRequestState() {
    if (gameRequestState.isLoaded) return;

    // Fetch initial game request list
    const initialEntries = await GameRequestEndpoint.getAll();
    initialEntries.forEach((gameRequest: GameRequestDto) => {
        gameRequestState.state[gameRequest.id] = gameRequest;
    });

    // Subscribe to real-time updates
    gameRequestState.subscription = GameRequestEndpoint.subscribe().onNext((gameRequestEvents: GameRequestEvent[]) => {
        gameRequestEvents.forEach((gameRequestEvent: GameRequestEvent) => {
            switch (gameRequestEvent.type) {
                case "created":
                case "updated":
                    //@ts-ignore
                    gameRequestState.state[gameRequestEvent.gameRequest.id] = gameRequestEvent.gameRequest;
                    break;
                case "deleted":
                    //@ts-ignore
                    delete gameRequestState.state[gameRequestEvent.gameRequestId];
                    break;
            }
        })
    });
}






