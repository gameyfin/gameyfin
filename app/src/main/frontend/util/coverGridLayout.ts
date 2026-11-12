// Constants for grid layout
const MIN_COLUMN_WIDTH = 180; // Minimum width per item (minmax value from original)
const MAX_COLUMN_WIDTH = 212; // Maximum width per item (minmax value from original)
export const COVER_GAP = 16; // gap-4 = 1rem = 16px
const ASPECT_RATIO = 12 / 17; // Game cover aspect ratio (width/height)

export function coverGridLayout(containerWidth: number, narrow: boolean) {
    const width = Math.max(0, containerWidth);
    const minimum = narrow ? 140 : MIN_COLUMN_WIDTH;
    // Calculate how many columns can fit
    const columnCount = Math.max(1, Math.floor((width + COVER_GAP) / (minimum + COVER_GAP)));
    // Calculate actual column width to distribute space evenly (up to MAX_COLUMN_WIDTH)
    const actualColumnWidth = Math.max(0, Math.min(MAX_COLUMN_WIDTH,
        Math.floor((width - (columnCount - 1) * COVER_GAP) / columnCount)));
    // Calculate cover height based on width and aspect ratio
    // GameCover's size prop is the height, so we need to calculate height from width
    return {columnCount, actualColumnWidth, coverHeight: Math.floor(actualColumnWidth / ASPECT_RATIO)};
}
