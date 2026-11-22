import {Button, Label, ListBox, Select, TextArea, toast, ToggleButton} from "@heroui/react";
import {HeartIcon, StarIcon} from "@phosphor-icons/react";
import {useEffect, useRef, useState} from "react";
import {useSnapshot} from "valtio/react";
import {CollectionEndpoint, GameSocialEndpoint} from "Frontend/generated/endpoints";
import CollectionKind from "Frontend/generated/org/gameyfin/app/collections/entities/CollectionKind";
import GameSocialDto from "Frontend/generated/org/gameyfin/app/social/GameSocialDto";
import PlayStatus from "Frontend/generated/org/gameyfin/app/social/PlayStatus";
import {collectionState} from "Frontend/state/CollectionState";
import {useAuth} from "Frontend/util/auth";
import {compoundRating} from "Frontend/util/utils";

const playOptions = [
    {id: "none", label: "No status"},
    {id: PlayStatus.WANT_TO_PLAY, label: "Want to play"},
    {id: PlayStatus.PLAYING, label: "Playing"},
    {id: PlayStatus.PLAYED, label: "Played"}
];

export function useGameSocial(gameId: number | undefined) {
    const memberId = useAuth().state.user?.id;
    const settings = useSnapshot(collectionState).settings;
    const scope = `${gameId}:${memberId ?? "guest"}`;
    const [result, setResult] = useState<{ scope: string; data: GameSocialDto }>();
    const [busy, setBusy] = useState(false);
    const [prompt, setPrompt] = useState(false);
    const [editing, setEditing] = useState(false);
    const request = useRef(0);
    const activeScope = useRef(scope);
    activeScope.current = scope;
    const data = result?.scope === scope ? result.data : undefined;

    async function load() {
        if (gameId === undefined) return;
        const token = ++request.current;
        const data = await GameSocialEndpoint.get(gameId);
        if (token === request.current && activeScope.current === scope) setResult({scope, data});
    }

    useEffect(() => {
        setBusy(false);
        setPrompt(false);
        setEditing(false);
        return () => {
            request.current++;
        };
    }, [scope]);

    useEffect(() => {
        if (settings?.reviewsEnabled === false) {
            setPrompt(false);
            setEditing(false);
        }
        void load().catch(() => toast.danger("Could not load game activity"));
    }, [scope, settings?.reviewsEnabled]);

    async function mutate<T>(action: () => Promise<T>, afterSave?: (result: T) => void) {
        if (busy) return;
        setBusy(true);
        try {
            const saved = await action();
            if (activeScope.current !== scope) return;
            afterSave?.(saved);
            await load();
        } catch {
            if (activeScope.current === scope) toast.danger("Could not save your changes");
        } finally {
            if (activeScope.current === scope) setBusy(false);
        }
    }

    return {
        gameId, memberId, data, busy, prompt, setPrompt, editing, setEditing, mutate,
        reviewsEnabled: settings?.reviewsEnabled !== false && data?.reviewsEnabled === true
    };
}

type GameSocial = ReturnType<typeof useGameSocial>;
const starValues = [1, 2, 3, 4, 5];

function externalStarRating(rating: number | undefined) {
    return rating == null ? undefined : Math.max(1, compoundRating({criticRating: rating}, [1, 5]));
}

function RatingScore({value, fallback = "Unavailable"}: { value?: number; fallback?: string }) {
    if (value == null) return <span className="text-muted">{fallback}</span>;

    return <span className="inline-flex items-center gap-2">
        <span className="inline-flex text-accent" aria-hidden="true">
            {starValues.map(star => {
                const fill = Math.min(1, Math.max(0, value - star + 1));
                return <span key={star} className="relative">
                    <StarIcon className="size-4"/>
                    <StarIcon weight="fill" className="absolute inset-0 size-4"
                              style={{clipPath: `inset(0 ${(1 - fill) * 100}% 0 0)`}}/>
                </span>;
            })}
        </span>
        <span className="whitespace-nowrap tabular-nums">{value.toFixed(1)}/5<span
            className="sr-only"> stars</span></span>
    </span>;
}

export function GameScores({data, critic, external}: {
    data?: GameSocialDto;
    critic?: number;
    external?: number;
}) {
    const votes = data?.ratingCount ?? 0;

    return <section aria-labelledby="game-ratings" className="flex flex-col gap-3">
        <h2 id="game-ratings" className="text-xl font-semibold">Ratings</h2>
        <dl className="grid gap-3 text-sm">
            <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-1">
                <dt className="text-muted">Critics</dt>
                <dd><RatingScore value={externalStarRating(critic)}/></dd>
            </div>
            <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-1">
                <dt className="text-muted">Players</dt>
                <dd><RatingScore value={externalStarRating(external)}/></dd>
            </div>
            <div className="flex flex-wrap items-start justify-between gap-x-3 gap-y-1">
                <dt className="text-muted">Gameyfin members</dt>
                <dd className="flex flex-col items-end">
                    <RatingScore value={data?.averageRating}
                                 fallback={data ? "No ratings yet" : "Loading…"}/>
                    {votes > 0 && <span className="text-xs text-muted">
                        {votes} {votes === 1 ? "vote" : "votes"}
                    </span>}
                </dd>
            </div>
        </dl>
    </section>;
}

export function GameFavouriteButton({social}: { social: GameSocial }) {
    const {gameId, memberId, busy, mutate} = social;
    const favourites = useSnapshot(collectionState).sorted.find(collection =>
        collection.kind === CollectionKind.FAVOURITES && collection.ownerId === memberId);
    if (gameId === undefined) return null;
    const favourite = favourites?.gameIds.includes(gameId) === true;

    return <ToggleButton isIconOnly className="size-12 shrink-0" aria-label="Favourite"
                         isSelected={favourite} isDisabled={busy || !favourites}
                         onChange={present => void mutate(() => CollectionEndpoint.setFavourite(gameId, present))}>
        <HeartIcon className="size-6" weight={favourite ? "fill" : "regular"}/>
    </ToggleButton>;
}

export function GameRatingInput({social}: { social: GameSocial }) {
    const [preview, setPreview] = useState<number>();
    const {gameId, memberId, data, busy, mutate} = social;
    if (gameId === undefined || memberId === undefined || !data) return null;
    const filled = preview ?? data.myRating ?? 0;

    return <div>
        <p id="your-rating-label" className="text-sm text-muted">Your rating</p>
        <div className="flex flex-wrap items-center gap-x-2">
            <div role="group" aria-labelledby="your-rating-label" className="flex"
                 onMouseLeave={() => setPreview(undefined)}>
                {starValues.map(stars =>
                    <button key={stars} type="button"
                            className="grid h-8 w-7 cursor-pointer place-items-center rounded-sm text-accent focus-visible:outline-2 focus-visible:outline-focus aria-disabled:cursor-default"
                            aria-label={`Rate ${stars} ${stars === 1 ? "star" : "stars"}`}
                            aria-pressed={data.myRating === stars} aria-disabled={busy}
                            onMouseEnter={() => setPreview(stars)}
                            onClick={() => void mutate(() => GameSocialEndpoint.rate(gameId, stars))}>
                        <StarIcon className="size-6" weight={stars <= filled ? "fill" : "regular"} aria-hidden="true"/>
                    </button>
                )}
            </div>
            {data.myRating != null && <Button size="sm" variant="ghost" isDisabled={busy}
                                              onPress={() => void mutate(() => GameSocialEndpoint.rate(gameId, undefined))}>Clear
                rating</Button>}
        </div>
    </div>;
}

export function GamePlayStatus({social}: { social: GameSocial }) {
    const {gameId, memberId, data, busy, mutate} = social;
    if (gameId === undefined || memberId === undefined || !data) return null;

    return <div className="flex flex-col items-start gap-2">
        <Select className="w-44 max-w-full" value={data.myPlayStatus ?? "none"} isDisabled={busy}
                onChange={key => {
                    const status = key === "none" ? undefined : key as PlayStatus;
                    void mutate(() => GameSocialEndpoint.setPlayStatus(gameId, status), social.setPrompt);
                }}>
            <Label>Your play status</Label>
            <Select.Trigger><Select.Value/><Select.Indicator/></Select.Trigger>
            <Select.Popover>
                <ListBox items={playOptions}>
                    {option => <ListBox.Item id={option.id} textValue={option.label}>
                        {option.label}<ListBox.ItemIndicator/>
                    </ListBox.Item>}
                </ListBox>
            </Select.Popover>
        </Select>
        {social.prompt && social.reviewsEnabled && !data.myReview &&
            <div role="status" className="flex flex-wrap items-center gap-2">
                <p>Marked as Played.</p>
                <Button onPress={() => {
                    social.setPrompt(false);
                    social.setEditing(true);
                }}>Write a review</Button>
                <Button variant="ghost" onPress={() => social.setPrompt(false)}>Skip</Button>
            </div>}
    </div>;
}

export function GameReviews({social}: { social: GameSocial }) {
    const {gameId, memberId, data, busy, editing, setEditing, mutate} = social;
    const own = data?.myReview;
    const [draft, setDraft] = useState("");
    const editor = useRef<HTMLTextAreaElement>(null);
    useEffect(() => {
        if (!editing) return;
        setDraft(own?.submittedText ?? own?.publishedText ?? "");
        editor.current?.focus();
    }, [editing]);

    if (!social.reviewsEnabled || gameId === undefined) return null;

    return <section aria-labelledby="game-reviews" className="flex flex-col gap-3">
        <h2 id="game-reviews" className="text-xl font-semibold">Reviews</h2>
        {memberId !== undefined && <div className="flex flex-col gap-2">
            {own?.submittedText && <div className="rounded-lg bg-surface p-3">
                <p className="font-medium">{own.rejected ? "Review rejected" : "Pending approval"}</p>
                <p className="whitespace-pre-wrap wrap-anywhere">{own.submittedText}</p>
            </div>}
            {editing ? <form className="flex flex-col gap-2" onSubmit={event => {
                event.preventDefault();
                void mutate(() => GameSocialEndpoint.saveReview(gameId, draft), () => setEditing(false));
            }}>
                <Label htmlFor="review-text">Your review</Label>
                <TextArea id="review-text" ref={editor} value={draft} onChange={event => setDraft(event.target.value)}
                          rows={5} maxLength={10000} required/>
                <div className="flex flex-wrap gap-2">
                    <Button type="submit" variant="primary" isPending={busy} isDisabled={!draft.trim()}>Save
                        review</Button>
                    <Button variant="ghost" onPress={() => setEditing(false)}>Cancel</Button>
                </div>
            </form> : <div className="flex flex-wrap gap-2">
                <Button onPress={() => {
                    social.setPrompt(false);
                    setEditing(true);
                }}>
                    {own ? "Edit your review" : "Write a review"}
                </Button>
                {own && <Button variant="danger-soft" isDisabled={busy}
                                onPress={() => void mutate(() => GameSocialEndpoint.deleteReview(gameId))}>Delete your
                    review</Button>}
            </div>}
        </div>}
        {!data?.reviews.length && <p className="text-muted">No published reviews yet.</p>}
        {data?.reviews.map(review => <article key={`${review.author}:${review.publishedAt}`}
                                              className="rounded-lg bg-surface p-4">
            <div className="flex flex-wrap gap-x-3 gap-y-1">
                <span className="font-medium wrap-anywhere">{review.author}</span>
                <time className="text-muted"
                      dateTime={review.publishedAt}>{new Date(review.publishedAt).toLocaleDateString()}</time>
            </div>
            <p className="mt-2 whitespace-pre-wrap wrap-anywhere">{review.text}</p>
        </article>)}
    </section>;
}
