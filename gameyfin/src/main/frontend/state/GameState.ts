import {Subscription} from "@vaadin/hilla-frontend";
import {proxy} from "valtio/index";
import {GameEndpoint} from "Frontend/generated/endpoints";
import GameEvent from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryEvent";
import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import Rand from "rand-seed";

type GameState = {
    subscription?: Subscription<GameEvent>;
    isLoaded: boolean;
    state: Record<number, GameDto>;
    games: GameDto[];
    gamesByLibraryId: Record<number, GameDto[]>;
    sortedByMostRecentlyAdded: GameDto[];
    sortedByMostRecentlyUpdated: GameDto[];
    randomlyOrderedGamesByLibraryId: Record<number, GameDto[]>;
};

export const gameState = proxy<GameState>({
    get isLoaded() {
        return this.subscription != null;
    },
    state: {},
    get games() {
        return Object.values<GameDto>(this.state);
    },
    get gamesByLibraryId() {
        return this.games.reduce((acc: Record<number, GameDto[]>, game: GameDto) => {
            (acc[game.libraryId] ||= []).push(game);
            return acc;
        }, {});
    },
    get sortedByMostRecentlyAdded() {
        return this.games
            .sort((a: GameDto, b: GameDto) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
    },
    get sortedByMostRecentlyUpdated() {
        return this.games
            .sort((a: GameDto, b: GameDto) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime());
    },
    get randomlyOrderedGamesByLibraryId() {
        const result: Record<number, GameDto[]> = {};
        for (const libraryId in this.gamesByLibraryId) {
            const rand = new Rand(libraryId.toString());
            result[libraryId] = this.gamesByLibraryId[libraryId]
                .filter((g: GameDto) => g.coverId && g.imageIds && g.imageIds.length > 0)
                .sort((a: GameDto, b: GameDto) => a.id - b.id)
                .sort(() => rand.next() - 0.5);
        }
        return result;
    }
});

/** Subscribe to and process state updates from backend **/
export async function initializeGameState() {
    if (gameState.isLoaded) return gameState;

    // Fetch initial library list
    const initialEntries = await GameEndpoint.getAll();
    initialEntries.forEach((game: GameDto) => {
        gameState.state[game.id] = game;
    });

    // Subscribe to real-time updates
    gameState.subscription = GameEndpoint.subscribe().onNext((gameEvent) => {
        switch (gameEvent.type) {
            case "created":
            case "updated":
                //@ts-ignore
                gameState.state[gameEvent.game.id] = gameEvent.game;
                break;
            case "deleted":
                //@ts-ignore
                delete gameState.state[gameEvent.gameId];
                break;
        }
    });

    return gameState;
}