import type GameSummaryDto from "Frontend/generated/org/gameyfin/app/games/dto/GameSummaryDto";

type GameMatchFields = Pick<GameSummaryDto, "id" | "title"> & {
    [Field in "genres" | "themes" | "developers"]?: readonly string[];
};

const matchFields = ["genres", "themes", "developers"] as const;
const titleOrder = new Intl.Collator(undefined, {sensitivity: "base"});

export function findRelatedGames<T extends GameMatchFields>(game: GameMatchFields, catalog: readonly T[]): T[] {
    const matchingValues = matchFields.map(field => ({field, values: new Set(game[field])}));

    return catalog
        .filter(candidate => candidate.id !== game.id)
        .map(candidate => {
            let score = 0;
            for (const {field, values} of matchingValues) {
                for (const value of new Set(candidate[field])) {
                    if (values.has(value)) score++;
                }
            }
            return {candidate, score};
        })
        .filter(({score}) => score > 0)
        .sort((a, b) => b.score - a.score ||
            titleOrder.compare(a.candidate.title, b.candidate.title) ||
            a.candidate.id - b.candidate.id)
        .map(({candidate}) => candidate);
}
