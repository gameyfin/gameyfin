export type Theme = {
    name?: string,
    colors: {
        /** Main accent color for this theme (replaces v2's `primary`). */
        accent: string,
        /** Secondary accent color, exposed via the custom `--gf-secondary` CSS variable. */
        secondary?: string,
    }
}