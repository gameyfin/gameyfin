import GameUpdateDto from "Frontend/generated/org/gameyfin/app/games/dto/GameUpdateDto";
import {getCsrfToken} from "Frontend/util/auth";
import {GameMetadataFormValues, metadataInitialValues, remoteImage} from "Frontend/util/gameMedia";
import type GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";

export interface GameUploadStatus {
    id: string;
    gameId?: number;
    completedFiles: number;
}

export async function uploadRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(`/game-uploads${path}`, {
        ...init, credentials: "same-origin",
        headers: {"X-CSRF-Token": getCsrfToken(), ...init.headers}
    });
    if (response.redirected) throw new Error("Your session has expired. Sign in again.");
    if (!response.ok) {
        const error = await response.json().catch(() => undefined);
        throw new Error(error?.message ?? error?.detail ?? `Upload failed (HTTP ${response.status}).`);
    }
    return response.status === 204 ? undefined as T : response.json();
}

export function uploadFile(id: string, index: number, file: File, signal: AbortSignal,
                           progress: (bytes: number) => void): Promise<void> {
    return new Promise((resolve, reject) => {
        const request = new XMLHttpRequest();
        const abort = () => request.abort();
        const cleanup = () => signal.removeEventListener("abort", abort);
        request.open("PUT", `/game-uploads/${id}/files/${index}`);
        request.setRequestHeader("Content-Type", "application/octet-stream");
        request.setRequestHeader("X-CSRF-Token", getCsrfToken());
        request.upload.onprogress = event => progress(event.loaded);
        request.onload = () => {
            cleanup();
            if (request.status === 204) resolve();
            else {
                let message = `Upload failed (HTTP ${request.status}).`;
                try { message = JSON.parse(request.responseText).message ?? message; } catch { /* Keep HTTP error. */ }
                reject(new Error(message));
            }
        };
        request.onerror = () => { cleanup(); reject(new Error("Upload interrupted. Retry starts from the beginning.")); };
        request.onabort = () => { cleanup(); reject(new Error("Upload cancelled. Retry starts from the beginning.")); };
        signal.addEventListener("abort", abort, {once: true});
        if (signal.aborted) { cleanup(); reject(new Error("Upload cancelled.")); }
        else request.send(file);
    });
}

export function uploadMetadataValues(metadata: GameUpdateDto, libraryId: number, path: string): GameMetadataFormValues {
    const values = metadataInitialValues({
        ...metadata, id: 0, libraryId, title: metadata.title ?? "", platforms: [...(metadata.platforms ?? [])],
        createdAt: "", updatedAt: "", cover: undefined, header: undefined, images: [],
        metadata: {path, fileSize: 0}, mediaOrder: metadata.mediaOrder ?? []
    } as GameDto);
    values.mediaCover = metadata.cover?.url ? remoteImage(metadata.cover.url) : undefined;
    values.mediaHeader = metadata.header?.url ? remoteImage(metadata.header.url) : undefined;
    values.mediaImages = (metadata.images ?? []).flatMap(image => image.url ? [remoteImage(image.url)] : []);
    return values;
}
