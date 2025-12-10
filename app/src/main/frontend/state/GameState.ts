import {Subscription} from "@vaadin/hilla-frontend";
import {proxy} from "valtio/index";
import {GameEndpoint} from "Frontend/generated/endpoints";
import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import Rand from "rand-seed";
import GameEvent from "Frontend/generated/org/gameyfin/app/games/dto/GameEvent";

type GameState = {
    subscription?: Subscription<GameEvent[]>;
    isLoaded: boolean;
    state: Record<number, GameDto>;
    games: GameDto[];
    gamesByLibraryId: Record<number, GameDto[]>;
    gamesByCollectionId: Record<number, GameDto[]>;
    sortedAlphabetically: GameDto[];
    randomlyOrderedGamesByLibraryId: Record<number, GameDto[]>;
    randomlyOrderedGamesByCollectionId: Record<number, GameDto[]>;
    knownPublishers: Set<string>;
    knownDevelopers: Set<string>;
    knownGenres: Set<string>;
    knownThemes: Set<string>;
    knownKeywords: Set<string>;
    knownFeatures: Set<string>;
    knownPerspectives: Set<string>;
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
        return this.sortedAlphabetically.reduce((acc: Record<number, GameDto[]>, game: GameDto) => {
            (acc[game.libraryId] ||= []).push(game);
            return acc;
        }, {});
    },
    get gamesByCollectionId() {
        return this.sortedAlphabetically.reduce((acc: Record<number, GameDto[]>, game: GameDto) => {
            game.collectionIds?.forEach((collectionId: number) => {
                (acc[collectionId] ||= []).push(game);
            });
            return acc;
        }, {});
    },
    get sortedAlphabetically() {
        return this.games
            .sort((a: GameDto, b: GameDto) => a.title.localeCompare(b.title, undefined, {sensitivity: 'base'}));
    },
    get randomlyOrderedGamesByLibraryId() {
        const result: Record<number, GameDto[]> = {};
        for (const libraryId in this.gamesByLibraryId) {
            const rand = new Rand(`library-${libraryId}`);
            result[libraryId] = this.gamesByLibraryId[libraryId]
                .sort((a: GameDto, b: GameDto) => a.id - b.id)
                .sort(() => rand.next() - 0.5);
        }
        return result;
    },
    get randomlyOrderedGamesByCollectionId() {
        const result: Record<number, GameDto[]> = {};
        for (const collectionId in this.gamesByCollectionId) {
            const rand = new Rand(`collection-${collectionId}`);
            result[collectionId] = this.gamesByCollectionId[collectionId]
                .sort((a: GameDto, b: GameDto) => a.id - b.id)
                .sort(() => rand.next() - 0.5);
        }
        return result;
    },
    get knownPublishers() {
        return new Set<string>(
            this.games
                .flatMap((game: GameDto) => game.publishers ? game.publishers : [])
                .sort()
        );
    },
    get knownDevelopers() {
        return new Set<string>(
            this.games
                .flatMap((game: GameDto) => game.developers ? game.developers : [])
                .sort()
        );
    },
    get knownGenres() {
        return new Set<string>(
            this.games
                .flatMap((game: GameDto) => game.genres ? game.genres : [])
                .sort()
        );
    },
    get knownThemes() {
        return new Set<string>(
            this.games
                .flatMap((game: GameDto) => game.themes ? game.themes : [])
                .sort()
        );
    },
    get knownKeywords() {
        return new Set<string>(
            this.games
                .flatMap((game: GameDto) => game.keywords ? game.keywords : [])
                .sort()
        );
    },
    get knownFeatures() {
        return new Set<string>(
            this.games
                .flatMap((game: GameDto) => game.features ? game.features : [])
                .sort()
        );
    },
    get knownPerspectives() {
        return new Set<string>(
            this.games
                .flatMap((game: GameDto) => game.perspectives ? game.perspectives : [])
                .sort()
        );
    }
});

/** Subscribe to and process state updates from backend **/
export async function initializeGameState() {
    if (gameState.isLoaded) return;

    // Fetch initial library list
    const initialEntries = await GameEndpoint.getAll();
    initialEntries.forEach((game: GameDto) => {
        gameState.state[game.id] = game;
    });

    // Subscribe to real-time updates
    gameState.subscription = GameEndpoint.subscribe().onNext((gameEvents: GameEvent[]) => {
        gameEvents.forEach((gameEvent: GameEvent) => {
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
        })
    });
}