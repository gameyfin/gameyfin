import {proxy} from "valtio";

export const layoutState = proxy({
    stickyHeader: true,
    headerHeight: 0,
});
