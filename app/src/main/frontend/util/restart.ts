import {SetupEndpoint} from "Frontend/generated/endpoints";
import {getCsrfToken} from "Frontend/util/auth";

const POLL_INTERVAL_MS = 1500;

function sleep(ms: number) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function isReachable(): Promise<boolean> {
    try {
        await SetupEndpoint.isSetupCompleted();
        return true;
    } catch {
        return false;
    }
}

/**
 * Waits until Gameyfin has been restarted, i.e. it became unreachable and is reachable again.
 * Resolves to `false` if Gameyfin did not come back within the given timeout.
 */
export async function waitForRestart(timeoutMs: number = 60 * 60 * 1000): Promise<boolean> {
    const start = Date.now();
    let wentDown = false;

    while (Date.now() - start < timeoutMs) {
        await sleep(POLL_INTERVAL_MS);
        const reachable = await isReachable();
        if (!reachable) {
            wentDown = true;
        } else if (wentDown) {
            return true;
        } else if (Date.now() - start > 2 * 60 * 1000) {
            // Restart was probably too fast to be noticed
            return true;
        }
    }
    return false;
}

/**
 * Uploads a file as raw request body (instead of multipart/form-data, which is size limited).
 * Throws an error containing the message returned by the server if the upload fails.
 */
export async function uploadFile(url: string, file: File): Promise<Response> {
    const response = await fetch(url, {
        method: "POST",
        credentials: "same-origin",
        headers: {
            "X-CSRF-Token": getCsrfToken(),
            "Content-Type": "application/octet-stream"
        },
        body: file
    });

    // A redirect (e.g. to the login page) means the request has not been processed
    if (response.redirected) {
        throw new Error("Upload failed: you are not authorized to perform this action");
    }

    if (!response.ok) {
        let message = `Upload failed (HTTP ${response.status})`;
        try {
            const body = await response.json();
            if (body?.message) message = body.message;
        } catch {
            // Response has no JSON body
        }
        throw new Error(message);
    }
    return response;
}
