import assert from "node:assert/strict";
import {test} from "node:test";
import {findRelatedGames} from "../../main/frontend/util/relatedGames.ts";

test("ranks distinct shared metadata and excludes the current game and unrelated games", () => {
    const game = {id: 1, title: "Current", genres: ["Puzzle"], themes: ["Mystery"], developers: ["Studio"]};
    const strongest = {id: 2, title: "Z", genres: ["Puzzle"], themes: ["Mystery"], developers: ["Studio"]};
    const sameTheme = {id: 3, title: "B", themes: ["Mystery"]};
    const repeatedGenre = {id: 4, title: "A", genres: ["Puzzle", "Puzzle", "Puzzle"]};
    const differentField = {id: 5, title: "Different field", themes: ["Puzzle"]};
    const missingMetadata = {id: 6, title: "Missing metadata"};

    assert.deepEqual(
        findRelatedGames(game, [game, sameTheme, differentField, strongest, repeatedGenre, missingMetadata]),
        [strongest, repeatedGenre, sameTheme]
    );
    assert.deepEqual(findRelatedGames(missingMetadata, [strongest, game]), []);
});

test("breaks score ties by title and ID across all matches", () => {
    const game = {id: 0, title: "Current", developers: ["Studio"]};
    const catalog = Array.from({length: 8}, (_, index) => ({
        id: 8 - index,
        title: index < 2 ? "Alpha" : "Beta",
        developers: ["Studio"]
    }));

    assert.deepEqual(findRelatedGames(game, catalog).map(({id}) => id), [7, 8, 1, 2, 3, 4, 5, 6]);
});
