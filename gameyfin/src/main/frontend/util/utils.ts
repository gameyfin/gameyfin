import {getCsrfToken} from "Frontend/util/auth";
import moment from 'moment-timezone';

export function roleToRoleName(role: string) {
    role = role.replace("ROLE_", "").toLowerCase();
    return role.charAt(0).toUpperCase() + role.slice(1);
}

export function toTitleCase(str: string) {
    return str.replaceAll("_", " ").toLowerCase().split(' ').map((word: string) => {
        return (word.charAt(0).toUpperCase() + word.slice(1));
    }).join(' ');
}

export function camelCaseToTitle(text: string): string {
    return text
        .replace(/([a-z])([A-Z])/g, '$1 $2')
        .replace(/^./, str => str.toUpperCase());
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
            return `${isPast ? '' : 'in'} ${value} ${unit.name}${value > 1 ? 's' : ''} ${isPast ? 'ago' : ''}`;
        }
    }

    return "just now";
}

export function timeBetween(start: string, end: string, timeZone: string = moment.tz.guess()): string {
    const startDate = moment.tz(start, timeZone);
    const endDate = moment.tz(end, timeZone);
    const diffInSeconds = startDate.diff(endDate, 'seconds');

    const units = [
        {name: "year", seconds: 31536000},
        {name: "month", seconds: 2592000},
        {name: "day", seconds: 86400},
        {name: "hour", seconds: 3600},
        {name: "minute", seconds: 60},
        {name: "second", seconds: 1}
    ];

    const absDiffInSeconds = Math.abs(diffInSeconds);

    for (const unit of units) {
        const value = Math.floor(absDiffInSeconds / unit.seconds);
        if (value >= 1) {
            return `${value} ${unit.name}${value > 1 ? 's' : ''}`;
        }
    }

    return "under a second";
}

/**
 * Format bytes as human-readable text.
 *
 * @param bytes Number of bytes.
 * @param si True to use metric (SI) units, aka powers of 1000. False to use
 *           binary (IEC), aka powers of 1024.
 * @param dp Number of decimal places to display.
 *
 * @return Formatted string.
 */
export function humanFileSize(bytes: number, si: boolean = false, dp: number = 1) {
    const thresh = si ? 1000 : 1024;

    if (Math.abs(bytes) < thresh) {
        return bytes + ' B';
    }

    const units = si
        ? ['kB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB']
        : ['KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB', 'ZiB', 'YiB'];
    let u = -1;
    const r = 10 ** dp;

    do {
        bytes /= thresh;
        ++u;
    } while (Math.round(Math.abs(bytes) * r) / r >= thresh && u < units.length - 1);


    return bytes.toFixed(dp) + ' ' + units[u];
}

/**
 * Return an object with the changed fields between two objects.
 * The returned object will only contain the changed fields with values from the current object.
 * @param initial
 * @param current
 */
export function deepDiff<T extends object>(initial: T, current: T): Partial<T> {
    function compareObjects(obj1: any, obj2: any): any {
        if (typeof obj1 !== 'object' || typeof obj2 !== 'object' || obj1 === null || obj2 === null) {
            if (obj1 !== obj2) {
                return obj2;
            }
            return undefined;
        }

        if (Array.isArray(obj1) && Array.isArray(obj2)) {
            if (obj1.length !== obj2.length) {
                return obj2;
            } else {
                const arrayDiff = obj1.map((item: any, index: number) => compareObjects(item, obj2[index]));
                if (arrayDiff.some(item => item !== undefined)) {
                    return arrayDiff;
                }
                return undefined;
            }
        }

        const keys = new Set([...Object.keys(obj1), ...Object.keys(obj2)]);
        const objDiff: any = {};
        keys.forEach(key => {
            const valueDiff = compareObjects(obj1[key], obj2[key]);
            if (valueDiff !== undefined) {
                objDiff[key] = valueDiff;
            }
        });

        if (Object.keys(objDiff).length > 0) {
            return objDiff;
        }
        return undefined;
    }

    const result = compareObjects(initial, current);
    return result || {};
}