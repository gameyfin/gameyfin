/**
 * Compares two ISO-8601 instants (as serialized by the backend) with full sub-millisecond precision.
 * Plain string comparison does not work, since the number of fractional digits varies.
 *
 * @return a negative number if a < b, 0 if equal, a positive number if a > b
 */
export function compareTimestamps(a: string | undefined, b: string | undefined): number {
    if (a === b) return 0;
    if (a === undefined) return -1;
    if (b === undefined) return 1;

    const [secondsA, nanosA] = parseTimestamp(a);
    const [secondsB, nanosB] = parseTimestamp(b);
    return secondsA !== secondsB ? secondsA - secondsB : nanosA - nanosB;
}

function parseTimestamp(timestamp: string): [number, number] {
    const match = /^(.*?:\d{2})(?:\.(\d+))?(Z|[+-]\d{2}:?\d{2})?$/.exec(timestamp);
    if (!match) return [Date.parse(timestamp) / 1000, 0];

    const [, dateTime, fraction = "", zone = "Z"] = match;
    const seconds = Date.parse(dateTime + zone) / 1000;
    const nanos = parseInt(fraction.padEnd(9, "0").substring(0, 9), 10);
    return [seconds, nanos];
}
