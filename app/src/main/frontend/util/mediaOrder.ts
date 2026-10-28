/** Consume each kind's ordered list, then append any media added since the order was saved. */
export function orderMedia<T>(images: readonly T[], videos: readonly T[], order: readonly string[] = []): T[] {
    let imageIndex = 0;
    let videoIndex = 0;
    const result: T[] = [];
    for (const kind of order) {
        if (kind === "image" && imageIndex < images.length) result.push(images[imageIndex++]);
        if (kind === "video" && videoIndex < videos.length) result.push(videos[videoIndex++]);
    }
    return [...result, ...images.slice(imageIndex), ...videos.slice(videoIndex)];
}
