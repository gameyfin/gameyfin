import {useSyncExternalStore, useCallback} from "react";

// Match Tailwind's default sm/xl breakpoints. Keep responsive-layout.md in sync.
export const NARROW_QUERY = "(width < 40rem)";
export const COMPACT_QUERY = "(width < 80rem)";

/** Subscribe only when markup must differ; use responsive CSS for ordinary layout. */
export function useMediaQuery(query: string): boolean {
    const subscribe = useCallback((notify: () => void) => {
        const media = window.matchMedia(query);
        media.addEventListener("change", notify);
        return () => media.removeEventListener("change", notify);
    }, [query]);
    return useSyncExternalStore(subscribe, () => window.matchMedia(query).matches, () => false);
}
