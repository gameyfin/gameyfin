import {Button, Link, Spinner} from "@heroui/react";
import {SparkleIcon, TagIcon, UsersIcon} from "@phosphor-icons/react";
import {useEffect, useState} from "react";
import {useSnapshot} from "valtio/react";
import CoverGrid from "Frontend/components/general/covers/CoverGrid";
import type {CoverBadge} from "Frontend/components/general/covers/GameCover";
import {RecommendationEndpoint} from "Frontend/generated/endpoints";
import DiscoveryResult from "Frontend/generated/org/gameyfin/app/social/recommendations/DiscoveryResult";
import DiscoveryKind from "Frontend/generated/org/gameyfin/app/social/recommendations/DiscoveryKind";
import {collectionState} from "Frontend/state/CollectionState";
import {gameState} from "Frontend/state/GameState";
import {useAuth} from "Frontend/util/auth";

export default function DiscoverView() {
    const memberId = useAuth().state.user?.id;
    const enabled = useSnapshot(collectionState).settings?.recommendationsEnabled;

    return <div className="flex flex-col gap-6">
        <h1 className="text-3xl font-bold">Discover</h1>
        {memberId !== undefined && enabled === true && <Recommendations key={memberId}/>}
        {memberId !== undefined && enabled === undefined && <Spinner aria-label="Loading Discover"/>}
        {enabled === false && <p className="text-muted">Game recommendations are disabled on this server.</p>}
    </div>;
}

function Recommendations() {
    const catalog = useSnapshot(gameState);
    const [result, setResult] = useState<DiscoveryResult>();
    const [failed, setFailed] = useState(false);
    const [attempt, setAttempt] = useState(0);

    useEffect(() => {
        let active = true;
        RecommendationEndpoint.discover().then(
            value => { if (active) setResult(value); },
            () => { if (active) setFailed(true); }
        );
        return () => { active = false; };
    }, [attempt]);

    if (failed) return <div className="flex flex-wrap items-center gap-4">
        <p role="alert">Could not load recommendations.</p>
        <Button variant="secondary" onPress={() => {
            setFailed(false);
            setAttempt(value => value + 1);
        }}>Retry</Button>
    </div>;

    if (!result || !catalog.isLoaded) return <Spinner aria-label="Loading game recommendations"/>;

    const coverBadges = new Map<number, readonly CoverBadge[]>();
    const popular = result.kind === DiscoveryKind.POPULAR;
    const games = result.recommendations.flatMap(({gameId, matchPercentage, similarToGameId, sharedTag}) => {
        const game = catalog.state[gameId];
        if (!game) return [];

        const badges: CoverBadge[] = [{text: `${matchPercentage}% ${popular ? "rating" : "match"}`}];
        if (!popular) {
            if (sharedTag) {
                const source = similarToGameId === undefined ? undefined : catalog.state[similarToGameId];
                badges.push({
                    text: sharedTag,
                    icon: TagIcon,
                    description: source ? `Shares ${sharedTag} with ${source.title}.` : `Matching tag: ${sharedTag}.`
                });
            } else if (similarToGameId === undefined) {
                badges.push({text: "Similar taste", icon: UsersIcon, description: "Members with similar taste liked this."});
            } else {
                badges.push({text: "Your preferences", icon: SparkleIcon, description: "Matches your game preferences."});
            }
        }
        coverBadges.set(gameId, badges);
        return [game];
    });

    if (!games.length) return <div className="flex flex-col items-start gap-2">
        <p className="text-muted">Add a favourite or rate a game to get recommendations.</p>
        <Link href="/search">Browse games</Link>
    </div>;

    return <section className="flex flex-col gap-4" aria-labelledby="discover-list-title">
        <h2 id="discover-list-title" className="text-xl font-semibold">
            {popular ? "Popular on this server" : "Recommended for you"}
        </h2>
        <CoverGrid games={games} coverBadges={coverBadges}/>
    </section>;
}
