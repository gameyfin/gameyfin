import type NotificationSeverity from "Frontend/generated/org/gameyfin/pluginapi/notifications/NotificationSeverity";
import {proxy} from "valtio";
import {NotificationEndpoint} from "Frontend/generated/endpoints";
import type NotificationDto from "Frontend/generated/org/gameyfin/app/core/notifications/NotificationDto";
import {liveSubscription} from "Frontend/state/sync/liveSubscription";

export const notificationState = proxy({
    items: [] as NotificationDto[],
    archived: false,
    severity: undefined as NotificationSeverity | undefined,
    pages: 1,
    total: 0,
    unreadCount: 0,
    loaded: false,
    error: ""
});

let identity: string | undefined;
let stop: ReturnType<typeof liveSubscription> | undefined;
const PAGE_SIZE = 25;

export function resetNotificationState() {
    stop?.();
    stop = undefined;
    identity = undefined;
    Object.assign(notificationState, {
        items: [],
        archived: false,
        severity: undefined,
        pages: 1,
        total: 0,
        unreadCount: 0,
        loaded: false,
        error: ""
    });
}

export function initializeNotificationState(nextIdentity: string) {
    if (identity === nextIdentity) return;
    resetNotificationState();
    identity = nextIdentity;
    stop = liveSubscription({
        subscribe: () => NotificationEndpoint.subscribe(),
        fetch: async () => {
            const {archived, pages, severity} = notificationState;
            const results = await Promise.all(Array.from({length: pages}, (_, page) =>
                NotificationEndpoint.getInbox(archived, page, PAGE_SIZE, severity)));
            return {archived, pages, severity, results};
        },
        replace: ({archived, pages, severity, results}) => {
            if (severity !== notificationState.severity || archived !== notificationState.archived || pages !== notificationState.pages) return;
            // Pagination may move during a concurrent change. Invalidation reconciles it; never render duplicate rows.
            notificationState.items = [...new Map(results.flatMap(page => page.items).map(item => [item.id, item])).values()];
            notificationState.total = results[0].total;
            notificationState.unreadCount = results[0].unreadCount;
            notificationState.loaded = true;
            notificationState.error = "";
        },
        apply: () => {
        },
        invalidates: true,
        onError: () => {
            notificationState.error = "Could not load notifications. Retrying…";
        }
    });
}

export function showNotificationArchive(archived: boolean) {
    Object.assign(notificationState, {archived, pages: 1, items: [], loaded: false, error: ""});
    void refreshNotifications();
}

export function loadMoreNotifications() {
    notificationState.pages++;
    void refreshNotifications();
}

export function refreshNotifications() {
    return stop?.refresh();
}

export function filterNotificationSeverity(severity: NotificationSeverity | undefined) {
    Object.assign(notificationState, {severity, pages: 1, items: [], loaded: false, error: ""});
    void refreshNotifications();
}
