import {useMemo} from "react";
import {useSnapshot} from "valtio";
import {gameState, randomlyOrdered} from "Frontend/state/GameState";
import {collectionState} from "Frontend/state/CollectionState";
import GameSummaryDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSummaryDto";
import Platform from "Frontend/generated/org/gameyfin/pluginapi/gamemetadata/Platform";

/*
 * Values derived from multiple states.
 * These were previously computed by the backend (and sent with every DTO), now they are computed on demand.
 */

export interface LibraryStats {
    gamesCount: number;
    /** Only available for admins */
    downloadedGamesCount: number;
}

export interface CollectionStats {
    gamesCount: number;
    /** Only available for admins */
    downloadCount: number;
    gamePlatforms: Platform[];
}

/** Games of each collection, sorted alphabetically */
export function useGamesByCollectionId(): Record<number, GameSummaryDto[]> {
    const games = useSnapshot(gameState);
    const collections = useSnapshot(collectionState);
    return useMemo(() => {
        const result: Record<number, GameSummaryDto[]> = {};
        for (const collection of Object.values(collections.state)) {
            result[collection.id] = collection.gameIds
                .map(id => games.state[id])
                .filter((game): game is GameSummaryDto => game !== undefined)
                .sort((a, b) => a.title.localeCompare(b.title, undefined, {sensitivity: 'base'}));
        }
        return result;
    }, [games.state, collections.state]);
}

/** Games of each collection in a deterministic random order */
export function useRandomlyOrderedGamesByCollectionId(): Record<number, GameSummaryDto[]> {
    const gamesByCollectionId = useGamesByCollectionId();
    return useMemo(() => {
        const result: Record<number, GameSummaryDto[]> = {};
        for (const collectionId in gamesByCollectionId) {
            result[collectionId] = randomlyOrdered(gamesByCollectionId[collectionId], `collection-${collectionId}`);
        }
        return result;
    }, [gamesByCollectionId]);
}

/** IDs of the collections containing the given game */
export function useCollectionIdsOfGame(gameId: number | undefined): number[] {
    const collections = useSnapshot(collectionState);
    return useMemo(() => {
        if (gameId === undefined) return [];
        return Object.values(collections.state)
            .filter(collection => collection.gameIds.includes(gameId))
            .map(collection => collection.id);
    }, [collections.state, gameId]);
}

export function useLibraryStats(libraryId: number): LibraryStats {
    const games = useSnapshot(gameState);
    return useMemo(() => {
        const libraryGames = Object.values(games.state).filter(game => game.libraryId === libraryId);
        return {
            gamesCount: libraryGames.length,
            downloadedGamesCount: libraryGames.reduce((sum, game) => sum + (game.downloadCount ?? 0), 0)
        };
    }, [games.state, libraryId]);
}

export function useCollectionStats(collectionId: number): CollectionStats | undefined {
    const games = useSnapshot(gameState);
    const collections = useSnapshot(collectionState);
    return useMemo(() => {
        const collection = collections.state[collectionId];
        if (!collection) return undefined;
        const collectionGames = collection.gameIds
            .map(id => games.state[id])
            .filter((game): game is GameSummaryDto => game !== undefined);
        return {
            gamesCount: collectionGames.length,
            downloadCount: collectionGames.reduce((sum, game) => sum + (game.downloadCount ?? 0), 0),
            gamePlatforms: [...new Set(collectionGames.flatMap(game => game.platforms))]
        };
    }, [games.state, collections.state, collectionId]);
}
