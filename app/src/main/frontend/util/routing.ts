import {useMatches} from 'react-router';

type RouteMetadata = {
    [key: string]: any;
};

/**
 * Returns the closest `handle` object with a `title` property from the current route or its parents.
 */
export function useRouteMetadata(): RouteMetadata | undefined {
    const matches = useMatches();
    // Walk up from the deepest match to the root
    for (let i = matches.length - 1; i >= 0; i--) {
        const handle = matches[i]?.handle as RouteMetadata | undefined;
        if (handle?.title) {
            return handle;
        }
    }
    // If no title found, return the deepest match's handle (if any)
    return matches[matches.length - 1]?.handle as RouteMetadata | undefined;
}
