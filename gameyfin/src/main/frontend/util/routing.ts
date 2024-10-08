import { useMatches } from 'react-router-dom';

type RouteMetadata = {
  [key: string]: any;
};

/**
 * Returns the `handle` object containing the metadata for the current route,
 * or undefined if the route does not have defined a handle.
 */
export function useRouteMetadata(): RouteMetadata | undefined {
  const matches = useMatches();
  const match = matches[matches.length - 1];
  return match?.handle as RouteMetadata | undefined;
}
