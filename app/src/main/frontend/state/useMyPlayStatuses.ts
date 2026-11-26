import {useEffect, useState} from "react";
import {GameSocialEndpoint} from "Frontend/generated/endpoints";
import PlayStatus from "Frontend/generated/org/gameyfin/app/social/PlayStatus";
import {useAuth} from "Frontend/util/auth";

export type PlayStatuses = Readonly<Partial<Record<number, PlayStatus>>>;

export function useMyPlayStatuses() {
    const memberId = useAuth().state.user?.id;
    const [result, setResult] = useState<{memberId: number; statuses?: PlayStatuses; failed?: boolean}>();

    useEffect(() => {
        setResult(undefined);
        if (memberId === undefined) return;

        const request = new AbortController();
        void GameSocialEndpoint.myPlayStatuses({signal: request.signal}).then(entries => {
            if (!request.signal.aborted) {
                setResult({memberId, statuses: Object.fromEntries(entries.map(({gameId, status}) => [gameId, status]))});
            }
        }).catch(error => {
            if (!request.signal.aborted) {
                console.error("Could not load play statuses", error);
                setResult({memberId, failed: true});
            }
        });

        return () => request.abort();
    }, [memberId]);

    return result?.memberId === memberId ? result : undefined;
}
