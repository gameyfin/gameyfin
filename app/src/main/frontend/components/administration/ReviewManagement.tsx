import {Button, Link, toast} from "@heroui/react";
import {ArrowClockwiseIcon} from "@phosphor-icons/react";
import {useCallback, useEffect, useState} from "react";
import {useSnapshot} from "valtio/react";
import ConfigFormField from "Frontend/components/administration/ConfigFormField";
import withConfigPage from "Frontend/components/administration/withConfigPage";
import {GameSocialEndpoint} from "Frontend/generated/endpoints";
import ReviewModerationEntry from "Frontend/generated/org/gameyfin/app/social/ReviewModerationEntry";
import {collectionState} from "Frontend/state/CollectionState";

const SocialSettingsForm = withConfigPage(({getConfig}) => <div className="grid gap-4 md:grid-cols-3">
    <ConfigFormField configElement={getConfig("social.enable-reviews")}/>
    <ConfigFormField configElement={getConfig("social.require-review-approval")}/>
    <ConfigFormField configElement={getConfig("social.allow-user-collections")}/>
    <ConfigFormField configElement={getConfig("social.enable-recommendations")}/>
</div>, "Social settings");

export function ReviewManagement() {
    const enabled = useSnapshot(collectionState).settings?.reviewsEnabled === true;
    const [entries, setEntries] = useState<ReviewModerationEntry[]>();
    const [busy, setBusy] = useState(false);
    const [stale, setStale] = useState(false);

    const refresh = useCallback(async () => {
        setBusy(true);
        try {
            setEntries(await GameSocialEndpoint.moderationQueue());
            setStale(false);
        } catch {
            toast.danger("Could not load reviews");
        } finally {
            setBusy(false);
        }
    }, []);

    useEffect(() => {
        if (enabled) void refresh();
        else setEntries(undefined);
    }, [enabled, refresh]);

    async function moderate(entry: ReviewModerationEntry, approve?: boolean) {
        setBusy(true);
        try {
            const changed = approve === undefined
                ? await GameSocialEndpoint.removePublished(entry.gameId, entry.memberId, entry.version)
                : await GameSocialEndpoint.moderate(entry.gameId, entry.memberId, entry.version, approve);
            if (changed) await refresh();
            else setStale(true);
        } catch {
            toast.danger("Could not update the review");
        } finally {
            setBusy(false);
        }
    }

    function renderEntries(pending: boolean) {
        const matching = entries?.filter(entry => entry.pending === pending);
        return matching?.length ? matching.map(entry => <article key={entry.version}
                                                                 className="flex flex-col gap-2 rounded-lg bg-surface p-4">
                <div className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                    <Link href={`/game/${entry.gameId}`} className="font-semibold wrap-anywhere">{entry.gameTitle}</Link>
                    <span className="wrap-anywhere">{entry.author}</span>
                    <time dateTime={entry.date} className="text-muted">{new Date(entry.date).toLocaleString()}</time>
                </div>
                <p className="whitespace-pre-wrap wrap-anywhere">{entry.text}</p>
                <div className="flex flex-wrap gap-2">
                    {pending ? <>
                        <Button isDisabled={busy || stale} onPress={() => void moderate(entry, true)}>Approve</Button>
                        <Button variant="danger-soft" isDisabled={busy || stale}
                                onPress={() => void moderate(entry, false)}>Reject</Button>
                    </> : <Button variant="danger-soft" isDisabled={busy || stale}
                                  onPress={() => void moderate(entry)}>Remove published review</Button>}
                </div>
            </article>) :
            <p className="text-muted">{entries ? `No ${pending ? "pending" : "published"} reviews.` : "Loading reviews…"}</p>;
    }

    return <div className="flex flex-col gap-6">
        <SocialSettingsForm/>
        {enabled && <section aria-labelledby="review-moderation" className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
                <h2 id="review-moderation" className="text-2xl font-bold">Review moderation</h2>
                <Button variant="outline" isPending={busy}
                        onPress={() => void refresh()}><ArrowClockwiseIcon/>Refresh</Button>
            </div>
            {stale && <p role="alert" className="text-danger">This review changed. Refresh the queue before moderating
                it.</p>}
            <h3 className="text-lg font-semibold">Pending approval</h3>
            {renderEntries(true)}
            <h3 className="text-lg font-semibold">Published reviews</h3>
            {renderEntries(false)}
        </section>}
    </div>;
}
