import {getCsrfToken} from "Frontend/util/auth";
import moment from 'moment-timezone';
import LibraryDto from "Frontend/generated/de/grimsi/gameyfin/libraries/dto/LibraryDto";
import Rand from "rand-seed";
import GameDto from "Frontend/generated/de/grimsi/gameyfin/games/dto/GameDto";
import {LibraryEndpoint} from "Frontend/generated/endpoints";

export function cssVar(variable: string) {
    return getComputedStyle(document.documentElement).getPropertyValue(`--${variable}`);
}

export function hsl(hsl: string) {
    return `hsl(${hsl}`;
}

export function rand(min: number, max: number) {
    const minCeiled = Math.ceil(min);
    const maxFloored = Math.floor(max);
    return Math.floor(Math.random() * (maxFloored - minCeiled) + minCeiled);
}

export function roleToRoleName(role: string) {
    role = role.replace("ROLE_", "").toLowerCase();
    return role.charAt(0).toUpperCase() + role.slice(1);
}

export function toTitleCase(str: string) {
    return str.replaceAll("_", " ").toLowerCase().split(' ').map((word: string) => {
        return (word.charAt(0).toUpperCase() + word.slice(1));
    }).join(' ');
}

export function roleToColor(role: string) {
    switch (role) {
        case "ROLE_SUPERADMIN":
            return "red";
        case "ROLE_ADMIN":
            return "orange";
        case "ROLE_USER":
            return "blue";
        default:
            return "gray";
    }
}

export async function fetchWithAuth(url: string, body: any = null, method = "POST"): Promise<Response> {
    return await fetch(url, {
        headers: {
            "X-CSRF-Token": getCsrfToken()
        },
        credentials: "same-origin",
        method: method,
        body: body
    });
}

/**
 * Calculate the time difference between a given Instant and the current time in the user's timezone.
 * @param {string} instantString - The Instant string returned by the backend.
 * @param {string} timeZone - The user's timezone.
 * @returns {string} - The time difference in a human-readable format.
 */
export function timeUntil(instantString: string, timeZone: string = moment.tz.guess()): string {
    const givenDate = moment.tz(instantString, timeZone);
    const now = moment.tz(timeZone);
    const diffInSeconds = givenDate.diff(now, 'seconds');

    const units = [
        {name: "year", seconds: 31536000},
        {name: "month", seconds: 2592000},
        {name: "day", seconds: 86400},
        {name: "hour", seconds: 3600},
        {name: "minute", seconds: 60},
        {name: "second", seconds: 1}
    ];

    const isPast = diffInSeconds < 0;
    const absDiffInSeconds = Math.abs(diffInSeconds);

    for (const unit of units) {
        const value = Math.floor(absDiffInSeconds / unit.seconds);
        if (value >= 1) {
            return `${isPast ? '-' : ''}${value} ${unit.name}${value > 1 ? 's' : ''}`;
        }
    }

    return "just now";
}

/**
 * Select a random number of games from the library based on the library ID.
 * @param library
 * @param count
 * @returns {GameDto[]}
 */
export async function randomGamesFromLibrary(library: LibraryDto, count?: number): Promise<GameDto[]> {
    const rand = new Rand(library.id.toString());
    const games = await LibraryEndpoint.getGamesInLibrary(library.id);
    return games
        .sort((a: GameDto, b: GameDto) => a.id - b.id)
        .sort(() => rand.next() - 0.5)
        .slice(0, count ?? games.length);
}