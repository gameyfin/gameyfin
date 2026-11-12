import {createContext} from "react";

/** Feature-owned navigation controls render in the compact header without moving their state. */
export const CompactHeaderActionsContext = createContext<HTMLDivElement | null>(null);
