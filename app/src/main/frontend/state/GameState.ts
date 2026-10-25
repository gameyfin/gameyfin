import {proxy} from "valtio";
import {GameEndpoint} from "Frontend/generated/endpoints";
import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import GameSummaryDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSummaryDto";
import GameEvent from "Frontend/generated/org/gameyfin/app/games/dto/GameEvent";
import GameField from "Frontend/generated/org/gameyfin/app/games/dto/GameField";
import GamePatchDto from "Frontend/generated/org/gameyfin/app/games/dto/GamePatchDto";
import Rand from "rand-seed";
import {createSyncedStore, SyncedEntityState, useFullEntity} from "Frontend/state/sync/createSyncedStore";
import {PatchMapping} from "Frontend/state/sync/patches";

type GameState = SyncedEntityState<GameSummaryDto, GameDto> & {
    games: GameSummaryDto[];
    gamesByLibraryId: Record<number, GameSummaryDto[]>;
    sortedAlphabetically: GameSummaryDto[];
    randomlyOrderedGamesByLibraryId: Record<number, GameSummaryDto[]>;
    knownPublishers: Set<string>;
    knownDevelopers: Set<string>;
    knownGenres: Set<string>;
    knownThemes: Set<string>;
    knownKeywords: Set<string>;
    knownFeatures: Set<string>;
    knownPerspectives: Set<string>;
};

export const gameState = proxy<GameState>({
    state: {},
    full: {},
    isLoaded: false,
    isSynced: false,
    get games() {
        return Object.values<GameSummaryDto>(this.state);
    },
    get gamesByLibraryId() {
        return this.sortedAlphabetically.reduce((acc: Record<number, GameSummaryDto[]>, game: GameSummaryDto) => {
            (acc[game.libraryId] ||= []).push(game);
            return acc;
        }, {});
    },
    get sortedAlphabetically() {
        return this.games
            .sort((a: GameSummaryDto, b: GameSummaryDto) => a.title.localeCompare(b.title, undefined, {sensitivity: 'base'}));
    },
    get randomlyOrderedGamesByLibraryId() {
        const result: Record<number, GameSummaryDto[]> = {};
        for (const libraryId in this.gamesByLibraryId) {
            result[libraryId] = randomlyOrdered(this.gamesByLibraryId[libraryId], `library-${libraryId}`);
        }
        return result;
    },
    get knownPublishers() {
        return knownValues(this.games, game => game.publishers);
    },
    get knownDevelopers() {
        return knownValues(this.games, game => game.developers);
    },
    get knownGenres() {
        return knownValues(this.games, game => game.genres);
    },
    get knownThemes() {
        return knownValues(this.games, game => game.themes);
    },
    get knownKeywords() {
        return knownValues(this.games, game => game.keywords);
    },
    get knownFeatures() {
        return knownValues(this.games, game => game.features);
    },
    get knownPerspectives() {
        return knownValues(this.games, game => game.perspectives);
    }
});

/** Deterministic random order, so the order stays stable across renders */
export function randomlyOrdered<T extends { id: number }>(games: readonly T[], seed: string): T[] {
    const rand = new Rand(seed);
    return [...games]
        .sort((a: T, b: T) => a.id - b.id)
        .sort(() => rand.next() - 0.5);
}

function knownValues(games: GameSummaryDto[], selector: (game: GameSummaryDto) => readonly string[] | undefined) {
    return new Set<string>(games.flatMap(game => selector(game) ?? []).sort());
}

/** Where the values of a patch are located in the summary and full DTO */
const gamePatchMapping: PatchMapping<GameField> = {
    [GameField.LIBRARY_ID]: [{value: "libraryId", summary: "libraryId", full: "libraryId"}],
    [GameField.TITLE]: [{value: "title", summary: "title", full: "title"}],
    [GameField.PLATFORMS]: [{value: "platforms", summary: "platforms", full: "platforms"}],
    [GameField.COVER]: [{value: "cover", summary: "cover", full: "cover"}],
    [GameField.HEADER]: [{value: "header", summary: "header", full: "header"}],
    [GameField.COMMENT]: [{value: "comment", full: "comment"}],
    [GameField.SUMMARY]: [{value: "summary", full: "summary"}],
    [GameField.RELEASE]: [{value: "release", summary: "release", full: "release"}],
    [GameField.USER_RATING]: [{value: "userRating", summary: "userRating", full: "userRating"}],
    [GameField.CRITIC_RATING]: [{value: "criticRating", summary: "criticRating", full: "criticRating"}],
    [GameField.PUBLISHERS]: [{value: "publishers", summary: "publishers", full: "publishers"}],
    [GameField.DEVELOPERS]: [{value: "developers", summary: "developers", full: "developers"}],
    [GameField.GENRES]: [{value: "genres", summary: "genres", full: "genres"}],
    [GameField.THEMES]: [{value: "themes", summary: "themes", full: "themes"}],
    [GameField.KEYWORDS]: [{value: "keywords", summary: "keywords", full: "keywords"}],
    [GameField.FEATURES]: [{value: "features", summary: "features", full: "features"}],
    [GameField.PERSPECTIVES]: [{value: "perspectives", summary: "perspectives", full: "perspectives"}],
    [GameField.IMAGES]: [
        {value: "images", full: "images"},
        {value: "previewImage", summary: "previewImage"}
    ],
    [GameField.VIDEO_URLS]: [{value: "videoUrls", full: "videoUrls"}],
    [GameField.MEDIA_ORDER]: [{value: "mediaOrder", full: "mediaOrder"}],
    [GameField.FILE_SIZE]: [{value: "fileSize", summary: "fileSize", full: "metadata.fileSize"}],
    [GameField.PATH]: [{value: "path", summary: "path", full: "metadata.path"}],
    [GameField.DOWNLOAD_COUNT]: [{value: "downloadCount", summary: "downloadCount", full: "metadata.downloadCount"}],
    [GameField.MATCH_CONFIRMED]: [{value: "matchConfirmed", summary: "matchConfirmed", full: "metadata.matchConfirmed"}],
    [GameField.FIELD_METADATA]: [{value: "fieldMetadata", full: "metadata.fields"}],
    [GameField.ORIGINAL_IDS]: [{value: "originalIds", full: "metadata.originalIds"}]
};

export const gameStore = createSyncedStore<GameSummaryDto, GameDto, GameEvent, GameField, GamePatchDto>(gameState, {
    name: "games",
    summaryStore: "games",
    fullStore: "gamesFull",
    fetchSync: async (since) => {
        const result = await GameEndpoint.sync(since);
        return {serverTime: result.serverTime, full: result.full, items: result.games, allIds: result.allIds};
    },
    fetchFull: (id) => GameEndpoint.getById(id),
    subscribe: () => GameEndpoint.subscribe(),
    normalizeEvent: (event) => event,
    patchMapping: gamePatchMapping
});

/** Subscribe to and process state updates from backend **/
export async function initializeGameState(identity: string) {
    await gameStore.start(identity);
}

/** Returns the full DTO of a game (loaded on demand) */
export function useFullGame(gameId: number | undefined) {
    return useFullEntity(gameState, gameStore, gameId);
}
