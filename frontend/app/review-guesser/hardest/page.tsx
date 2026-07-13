import type {Metadata} from "next";
import Link from "next/link";
import {BACKEND_ORIGIN as backend} from "@/lib/backend";
import {buildBreadcrumbJsonLd} from "@/lib/seo";
import {Routes} from "../../routes";
import "@/styles/components/leaderboard.css";
import "@/styles/components/seasons.css";

type DeceptionDirection = "over" | "under" | "none";

type HardestGame = {
    appId: number;
    appName: string;
    avgScore: number;
    playerCount: number;
    deceptionRate: number;
    deceptionDirection: DeceptionDirection;
    mostCommonWrongBucket: string | null;
    mostCommonWrongBucketCount: number | null;
    actualBucket: string;
    latestPickDate: string;
};

async function loadHardestGames(): Promise<HardestGame[] | null> {
    try {
        const res = await fetch(`${backend}/api/stats/game/hardest`, {
            next: {revalidate: 3600, tags: ["stats-hardest-games"]}
        });
        if (!res.ok) return null;
        return await res.json() as HardestGame[];
    } catch {
        return null;
    }
}

function formatDeception(game: HardestGame): string {
    if (game.deceptionDirection === "none") return "—";
    const pct = Math.round(game.deceptionRate * 100);
    const emoji = game.deceptionDirection === "over" ? "🔺" : "🔻";
    const label = game.deceptionDirection === "over" ? "over-guessed" : "under-guessed";
    return `${emoji} ${label} ${pct}%`;
}

function formatMostMissed(game: HardestGame): string {
    if (!game.mostCommonWrongBucket || game.mostCommonWrongBucketCount == null) {
        return game.actualBucket ? `— → ✓ ${game.actualBucket}` : "—";
    }
    return `${game.mostCommonWrongBucket} (${game.mostCommonWrongBucketCount}×) → ✓ ${game.actualBucket}`;
}

export default async function HardestGamesPage() {
    const games = await loadHardestGames();
    const breadcrumbJsonLd = buildBreadcrumbJsonLd([
        {name: "Home", url: Routes.home},
        {name: "Hardest Games", url: Routes.hardestGames},
    ]);

    return (
        <section className="container seasons">
            <script type="application/ld+json" dangerouslySetInnerHTML={{
                __html: JSON.stringify(breadcrumbJsonLd)
            }}/>
            <header className="seasons__hero">
                <h1>Hardest Games</h1>
                <p className="seasons__intro">
                    The Steam games players struggle with the most — ranked by lowest average score.
                    Deception metrics show whether players tend to over- or under-guess review counts,
                    and which review bucket is most frequently missed.
                </p>
            </header>

            {games === null ? (
                <p className="text-muted">Failed to load hardest games. Please try again soon.</p>
            ) : games.length === 0 ? (
                <p className="text-muted">No games available yet. Check back once more rounds have been played.</p>
            ) : (
                <div className="leaderboard">
                    <div className="leaderboard__scroll">
                        <table className="leaderboard__table" aria-label="Hardest games ranked by lowest average score">
                            <thead>
                            <tr>
                                <th scope="col" className="num" title="Rank by difficulty (1 = hardest)">#</th>
                                <th scope="col" style={{maxWidth: '220px'}} title="Steam game name with links to the Steam store and the archive page for the latest pick date">Game</th>
                                <th scope="col" className="num" title="Average points scored by all players on this game (0–5 scale)">Avg Score</th>
                                <th scope="col" className="num" title="Number of unique players who guessed this game">Players</th>
                                <th scope="col" title="Whether players consistently over-guessed (🔺) or under-guessed (🔻) the review count, and what percentage guessed in that wrong direction">Deception</th>
                                <th scope="col" title="The review-count bucket most frequently chosen when players guessed wrong (and how many times), plus the correct answer">Most Missed</th>
                            </tr>
                            </thead>
                            <tbody>
                            {games.map((game, i) => (
                                <tr key={game.appId}>
                                    <td className="num">{i + 1}</td>
                                    <td>
                                        <span
                                            className="leaderboard__profile-link"
                                            style={{
                                                display: 'block',
                                                maxWidth: '220px',
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis',
                                                whiteSpace: 'nowrap'
                                            }}
                                            title={game.appName}
                                        >
                                            {game.appName}
                                        </span>
                                        <span className="hardest__links">
                                            <a
                                                href={`https://store.steampowered.com/app/${game.appId}`}
                                                target="_blank"
                                                rel="noopener noreferrer"
                                            >
                                                Steam ↗
                                            </a>
                                            {" · "}
                                            <Link href={`/review-guesser/archive/${game.latestPickDate}`}>
                                                Archive
                                            </Link>
                                        </span>
                                    </td>
                                    <td className="num">{game.avgScore.toFixed(1)} / 5</td>
                                    <td className="num">{game.playerCount}</td>
                                    <td>{formatDeception(game)}</td>
                                    <td>{formatMostMissed(game)}</td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
        </section>
    );
}

export const metadata: Metadata = {
    title: "Hardest Games — Steam Review Guesser",
    description: "The Steam games players struggle with the most, ranked by lowest average score, with deception metrics and most-missed review buckets.",
    alternates: {
        canonical: Routes.hardestGames
    },
    keywords: [
        "hardest games",
        "Steam games",
        "deception",
        "review guessing",
        "statistics",
        "difficulty"
    ],
    openGraph: {
        title: "Hardest Games — Steam Review Guesser",
        description: "The Steam games players struggle with the most, with deception metrics.",
        url: Routes.hardestGames,
        images: ["/opengraph-image"]
    },
    twitter: {
        card: "summary_large_image",
        title: "Hardest Games — Steam Review Guesser",
        description: "The Steam games players struggle with the most, with deception metrics.",
        images: ["/opengraph-image"]
    }
};

export const revalidate = 3600;
