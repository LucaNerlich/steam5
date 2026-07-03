import type {Metadata} from "next";
import Link from "next/link";
import {BACKEND_ORIGIN as backend} from "@/lib/backend";
import {ACHIEVEMENT_LABELS, ACHIEVEMENT_TITLES} from "@/lib/achievements";
import {INSIGHT_EMOJI, type InsightType} from "@/components/PlayerSpotlight";
import type {BucketsResponse} from "@/types/review-game";
import {Routes} from "../routes";
import "@/styles/components/reviewRules.css";
import "@/styles/components/howToPlay.css";

export const metadata: Metadata = {
    title: "How to Play",
    description: "Learn how Steam5 works: guessing review buckets, scoring, streaks, achievements, and the Player Spotlight.",
    alternates: {canonical: "/how-to-play"},
    robots: {index: true, follow: true},
    openGraph: {
        title: 'How to Play',
        description: 'Learn how Steam5 works: guessing review buckets, scoring, streaks, achievements, and the Player Spotlight.',
        url: '/how-to-play',
        images: ['/opengraph-image'],
    },
};

type Buckets = { labels: string[]; titles: string[] };

async function loadBuckets(): Promise<Buckets> {
    try {
        const res = await fetch(`${backend}/api/review-game/buckets`, {
            next: {revalidate: 3600},
            headers: {"accept": "application/json"},
        });
        if (!res.ok) return {labels: [], titles: []};
        const data: BucketsResponse = await res.json();
        if (Array.isArray(data)) return {labels: data, titles: []};
        return {labels: data.buckets ?? [], titles: data.bucketTitles ?? []};
    } catch {
        return {labels: [], titles: []};
    }
}

const SPOTLIGHT_TIERS: { type: InsightType; name: string; description: string }[] = [
    {type: "DAY_STREAK", name: "Day Streak", description: "You've played at least 5 days in a row."},
    {type: "BEST_DAY_EVER", name: "Best Day Ever", description: "Yesterday's score beat your own previous best day."},
    {
        type: "BEAT_THE_ODDS",
        name: "Beat the Odds",
        description: "You scored well on the previous day's toughest round — the one most players missed.",
    },
    {
        type: "WELCOME_BACK",
        name: "Welcome Back",
        description: "You returned after a few days away and played well on your comeback day.",
    },
    {
        type: "MOST_IMPROVED",
        name: "Most Improved",
        description: "Your average score over the last month is meaningfully better than the month before.",
    },
    {
        type: "HOT_STREAK",
        name: "Hot Streak",
        description: "Your recent two-week average is well above your all-time average.",
    },
    {
        type: "WEEKLY_ACHIEVEMENT",
        name: "Weekly Achievement",
        description: "You earned one of the weekly leaderboard badges.",
    },
    {
        type: "MILESTONE",
        name: "Milestone",
        description: "You're near or just crossed a rounds-played or lifetime-points milestone.",
    },
];

export default async function HowToPlayPage() {
    const buckets = await loadBuckets();

    return (
        <section className="container how-to-play">
            <h1>How to Play</h1>
            <p>
                Steam5 picks five random Steam games every day. Your job isn&apos;t to say whether a game is good or
                bad &mdash; it&apos;s to guess how many total reviews it has on Steam. Games with huge review counts
                are obvious blockbusters; games with a handful of reviews are niche gems. Guess the right bucket,
                score points, and climb the leaderboards.
            </p>

            <div className="review-rules">
                <h3>Quick recap</h3>
                <ul>
                    <li>5 rounds a day, one guess per round.</li>
                    <li>Guess the review-count bucket, not the sentiment.</li>
                    <li>Closer guesses score more points; an exact match scores the max.</li>
                    <li>Sign in with Steam to save streaks, achievements, and your leaderboard spot.</li>
                </ul>
            </div>

            <h2>How a round works</h2>
            <p>
                Each day (reset at midnight UTC) presents 5 rounds. For every round you see a Steam game and pick the
                bucket you think matches its total review count. Once you submit a guess it&apos;s locked in &mdash;
                you can&apos;t change it. Guessing while signed out still works, but those guesses aren&apos;t saved
                to your streak, achievements, or the leaderboard. Sign in with Steam first if you want your progress
                to count.
            </p>

            <h2>Review buckets &amp; strategy</h2>
            <p>
                Every game on Steam5 falls into one of a handful of review-count buckets, from barely-reviewed to
                genuine blockbuster:
            </p>
            {buckets.labels.length > 0 ? (
                <ul className="how-to-play__buckets">
                    {buckets.labels.map((label, i) => (
                        <li key={label}>
                            <strong>{label}</strong> reviews
                            {buckets.titles[i] ? <> &mdash; &ldquo;{buckets.titles[i]}&rdquo;</> : null}
                        </li>
                    ))}
                </ul>
            ) : null}
            <p>
                Since sentiment doesn&apos;t matter, think in terms of visibility and reach instead: is this a
                well-known franchise or a AAA release you&apos;d recognize from the Steam front page? It&apos;s
                probably a high bucket. Never heard of it and looks like a small passion project? Probably low. The
                daily mix of well-known and obscure titles changes every day, so it helps to judge each round
                relative to the others rather than in isolation.
            </p>

            <h2>Scoring</h2>
            <p>Points are based on how far your guess was from the actual bucket:</p>
            <ul>
                <li>Exact bucket &mdash; 5 points (&ldquo;Hit!&rdquo;)</li>
                <li>Off by one bucket &mdash; 3 points</li>
                <li>Off by two buckets &mdash; 1 point</li>
                <li>Off by three or more &mdash; 0 points (&ldquo;Flop!&rdquo;)</li>
            </ul>
            <p>A perfect day &mdash; five exact hits &mdash; is worth 25 points.</p>

            <h2>Streaks</h2>
            <p>
                Your current streak counts consecutive days you&apos;ve played (with a one-day grace period if
                you haven&apos;t played yet today). Your longest streak is the best run you&apos;ve ever had.
                Both are tracked on your profile.
            </p>

            <h2>Leaderboards &amp; seasons</h2>
            <p>
                Every guess you make while signed in counts toward several leaderboards:{" "}
                <Link href={Routes.leaderboardToday}>today</Link>,{" "}
                <Link href={Routes.leaderboard}>all-time</Link>,{" "}
                <Link href={Routes.leaderboardWeekly}>weekly</Link>, and{" "}
                <Link href={Routes.leaderboardSeason}>the current season</Link>. Seasons run on a recurring
                schedule &mdash; see the <Link href={Routes.seasons}>seasons overview</Link> for details and past
                results. Missed a day? Browse every past round in the <Link href={Routes.archive}>archive</Link>.
            </p>

            <h2>Achievements</h2>
            <p>
                Achievements are awarded per leaderboard period (daily, weekly, monthly, season, and all-time) to
                the single top performer in each category, once a minimum number of rounds have been played to
                keep results meaningful:
            </p>
            <ul className="how-to-play__achievements">
                {Object.entries(ACHIEVEMENT_LABELS).map(([key, label]) => (
                    <li key={key}>
                        <strong>{label}</strong> &mdash; {ACHIEVEMENT_TITLES[key]}
                    </li>
                ))}
            </ul>

            <h2>Player Spotlight</h2>
            <p>
                Once you&apos;ve played at least 70 rounds and have been active in the last two weeks,
                you&apos;re eligible for the daily &ldquo;Good vibes&rdquo; Player Spotlight shown at the top of
                round 1. Each day one eligible player is featured for one of these highlights:
            </p>
            <ul className="how-to-play__spotlight">
                {SPOTLIGHT_TIERS.map(tier => (
                    <li key={tier.type}>
                        <span className="how-to-play__spotlight-icon" aria-hidden="true">{INSIGHT_EMOJI[tier.type]}</span>
                        <strong>{tier.name}</strong> &mdash; {tier.description}
                    </li>
                ))}
            </ul>
        </section>
    );
}
