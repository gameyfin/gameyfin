import {decode} from "blurhash";

/** How often an image which could not be loaded (e.g. because it is still being downloaded) is retried */
export const MAX_IMAGE_RETRIES = 15;
const MAX_RETRY_DELAY_MS = 60_000;

const placeholderCache = new Map<string, string | undefined>();

/** Decodes a blurhash into a small data URL which can be used as image source */
export function blurhashToDataUrl(blurhash: string, width: number, height: number): string | undefined {
    const key = `${blurhash}-${width}x${height}`;
    if (placeholderCache.has(key)) return placeholderCache.get(key);

    let dataUrl: string | undefined;
    try {
        const canvas = document.createElement("canvas");
        canvas.width = width;
        canvas.height = height;
        const ctx = canvas.getContext("2d");
        if (ctx) {
            const imageData = ctx.createImageData(width, height);
            imageData.data.set(decode(blurhash, width, height));
            ctx.putImageData(imageData, 0, 0);
            dataUrl = canvas.toDataURL();
        }
    } catch (e) {
        console.error("Failed to decode blurhash:", e);
    }
    placeholderCache.set(key, dataUrl);
    return dataUrl;
}

/** Delay before the given retry of an image, increases with every attempt */
export function imageRetryDelay(attempt: number): number {
    return Math.min(5000 * 2 ** attempt, MAX_RETRY_DELAY_MS);
}

/** Adds a parameter for retries to bypass the browser cache, so a failed request is not reused */
export function withRetryParam(src: string, attempt: number): string {
    return attempt === 0 ? src : `${src}${src.includes("?") ? "&" : "?"}retry=${attempt}`;
}
