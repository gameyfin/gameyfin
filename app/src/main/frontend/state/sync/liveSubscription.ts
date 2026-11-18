import {ActionOnLostSubscription, Subscription} from "@vaadin/hilla-frontend";

/** Subscribe first, reconcile a snapshot, and ignore results after disposal. */
export function liveSubscription<T, E>(options: {
    subscribe: () => Subscription<E[]>;
    fetch: () => Promise<T>;
    replace: (value: T) => void;
    apply: (events: E[]) => void;
    /** Invalidation-only streams carry no entities; every event requests a fresh snapshot. */
    invalidates?: boolean;
    onError?: (error: unknown) => void;
}) {
    let disposed = false;
    let buffer: E[] | undefined = [];
    let running = false;
    let rerun = false;
    let retry: ReturnType<typeof setTimeout> | undefined;
    let subscription: Subscription<E[]> | undefined;

    async function sync() {
        if (disposed) return;
        if (running) {
            rerun = true;
            return;
        }
        running = true;
        buffer ??= [];
        try {
            const result = await options.fetch();
            if (disposed) return;
            options.replace(result);
            options.apply(buffer);
            buffer = undefined;
        } catch (error) {
            if (!disposed) {
                options.onError?.(error);
                console.error("Failed to synchronize live state", error);
                clearTimeout(retry);
                retry = setTimeout(() => void sync(), 10_000);
            }
        } finally {
            running = false;
            if (rerun && !disposed) {
                rerun = false;
                void sync();
            }
        }
    }

    function connect() {
        if (disposed) return;
        subscription = options.subscribe()
            .onNext(events => {
                if (disposed) return;
                if (options.invalidates) {
                    void sync();
                    return;
                }
                if (buffer) buffer.push(...events);
                else options.apply(events);
            })
            .onConnectionStateChange(event => {
                if (event.detail.state === "connected") void sync();
            })
            .onSubscriptionLost(() => {
                if (disposed) return ActionOnLostSubscription.REMOVE;
                void sync();
                return ActionOnLostSubscription.RESUBSCRIBE;
            })
            .onError(() => {
                if (disposed) return;
                subscription?.cancel();
                clearTimeout(retry);
                retry = setTimeout(connect, 10_000);
            });
        void sync();
    }

    connect();
    return Object.assign(() => {
        disposed = true;
        clearTimeout(retry);
        subscription?.cancel();
    }, {refresh: sync});
}
