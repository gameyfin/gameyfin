import GameDto from "Frontend/generated/org/gameyfin/app/games/dto/GameDto";
import GameUpdateDto from "Frontend/generated/org/gameyfin/app/games/dto/GameUpdateDto";
import GameImageSelectionDto from "Frontend/generated/org/gameyfin/app/games/dto/GameImageSelectionDto";
import {GameEndpoint} from "Frontend/generated/endpoints";
import {getCsrfToken} from "Frontend/util/auth";
import {orderMedia} from "./mediaOrder";

export const MAX_IMAGE_SIZE = 20 * 1024 * 1024;
export const MAX_MEDIA_SIZE = 100 * 1024 * 1024;
export const IMAGE_ACCEPT = "image/jpeg,image/png,image/webp,image/gif";
export type MediaKind = "cover" | "header" | "screenshot";

export interface DraftImage {
    key: string;
    selection: GameImageSelectionDto;
    previewUrl: string;
    file?: File;
}

export interface MediaDraft {
    mediaCover?: DraftImage;
    mediaHeader?: DraftImage;
    mediaImages: DraftImage[];
    mediaVideos: string[];
    mediaOrder?: string[];
}

export type GameMetadataFormValues = GameDto & MediaDraft;

export function metadataInitialValues(game: GameDto): GameMetadataFormValues {
    // Valtio updates must not reinitialize an open form.
    const snapshot = JSON.parse(JSON.stringify(game)) as GameDto;
    const existing = (id: number, kind: MediaKind): DraftImage => ({
        key: `image-${id}`, selection: {id}, previewUrl: `images/${kind}/${id}`
    });
    return {
        ...snapshot,
        developers: snapshot.developers ?? [],
        publishers: snapshot.publishers ?? [],
        genres: snapshot.genres ?? [],
        themes: snapshot.themes ?? [],
        features: snapshot.features ?? [],
        perspectives: snapshot.perspectives ?? [],
        keywords: snapshot.keywords ?? [],
        mediaCover: snapshot.cover ? existing(snapshot.cover.id, "cover") : undefined,
        mediaHeader: snapshot.header ? existing(snapshot.header.id, "header") : undefined,
        mediaImages: (snapshot.images ?? []).map(image => existing(image.id, "screenshot")),
        mediaVideos: [...(snapshot.videoUrls ?? [])]
    };
}

export function allDraftImages(values: MediaDraft): DraftImage[] {
    return [values.mediaCover, values.mediaHeader, ...values.mediaImages].filter((image): image is DraftImage => !!image);
}

export function remoteImage(url: string): DraftImage {
    const trimmed = url.trim();
    const parsed = new URL(trimmed);
    if (!["http:", "https:"].includes(parsed.protocol)) throw new Error("Enter an HTTP(S) image URL.");
    return {key: `url-${trimmed}`, selection: {url: trimmed}, previewUrl: trimmed};
}

export async function uploadedImages(files: File[]): Promise<DraftImage[]> {
    if (files.reduce((size, file) => size + file.size, 0) > MAX_MEDIA_SIZE) {
        throw new Error("The selected uploads exceed 100 MiB. Save fewer images at a time.");
    }
    const created: DraftImage[] = [];
    try {
        for (const file of files) {
            if (file.size === 0 || file.size > MAX_IMAGE_SIZE) throw new Error("Each image must be between 1 byte and 20 MiB.");
            if (!IMAGE_ACCEPT.split(",").includes(file.type)) throw new Error("Choose JPEG, PNG, WebP, or GIF images.");
            const key = `upload-${crypto.randomUUID()}`;
            const previewUrl = URL.createObjectURL(file);
            const draft = {key, selection: {uploadKey: key}, previewUrl, file};
            created.push(draft);
            await new Promise<void>((resolve, reject) => {
                const image = new Image();
                image.onload = () => resolve();
                image.onerror = () => reject(new Error(`${file.name} is not a valid image.`));
                image.src = previewUrl;
            });
        }
        return created;
    } catch (error) {
        created.forEach(image => URL.revokeObjectURL(image.previewUrl));
        throw error;
    }
}

export function metadataUpdate(initial: GameMetadataFormValues, values: GameMetadataFormValues): GameUpdateDto {
    const update: GameUpdateDto = {id: initial.id};
    const fields = ["title", "platforms", "release", "comment", "summary", "developers", "publishers",
        "genres", "themes", "features", "perspectives", "keywords"] as const;
    for (const field of fields) {
        if (JSON.stringify(initial[field]) !== JSON.stringify(values[field])) {
            Object.assign(update, {[field]: values[field]});
        }
    }
    if (initial.mediaCover?.key !== values.mediaCover?.key) update.cover = values.mediaCover?.selection;
    if (initial.mediaHeader?.key !== values.mediaHeader?.key) update.header = values.mediaHeader?.selection;
    const images = values.mediaImages.map(image => image.selection);
    if (JSON.stringify(initial.mediaImages.map(image => image.selection)) !== JSON.stringify(images)) update.images = images;
    if (JSON.stringify(initial.mediaVideos) !== JSON.stringify(values.mediaVideos)) update.videoUrls = values.mediaVideos;
    const galleryOrder = (draft: MediaDraft) => orderMedia(
        draft.mediaImages.map(() => "image"), draft.mediaVideos.map(() => "video"), draft.mediaOrder);
    if (JSON.stringify(galleryOrder(initial)) !== JSON.stringify(galleryOrder(values))) {
        update.mediaOrder = galleryOrder(values);
    }
    return update;
}

export interface ManualGameImport {
    path: string;
    libraryId: number;
    platforms: GameDto["platforms"];
}

export async function saveMetadata(update: GameUpdateDto, values: MediaDraft, newGame?: ManualGameImport): Promise<void> {
    const images = allDraftImages(values);
    const files = images.filter(image => image.file);
    if (files.length === 0 && !newGame) {
        await GameEndpoint.updateGame(update);
        return;
    }
    if (files.reduce((size, image) => size + image.file!.size, 0) > MAX_MEDIA_SIZE) {
        throw new Error("The selected uploads exceed 100 MiB. Save fewer images at a time.");
    }
    const body = new FormData();
    body.append("metadata", new Blob([JSON.stringify(update)], {type: "application/json"}));
    files.forEach(image => body.append(image.selection.uploadKey!, image.file!));
    if (newGame) {
        body.append("path", newGame.path);
        body.append("libraryId", String(newGame.libraryId));
    }
    const response = await fetch(newGame ? "game-media/import" : "game-media/metadata", {
        method: "POST", credentials: "same-origin", headers: {"X-CSRF-Token": getCsrfToken()}, body
    });
    if (response.redirected) throw new Error("Your session has expired. Sign in again to save changes.");
    if (!response.ok) {
        const detail = await response.json().catch(() => undefined);
        throw new Error(detail?.detail ?? detail?.message ?? `Saving metadata failed (HTTP ${response.status}).`);
    }
}
