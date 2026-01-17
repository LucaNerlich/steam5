export * from "../../seasons/[seasonNumber]/page";
import type {Metadata} from "next";
import Image from "next/image";
import Link from "next/link";
import {notFound} from "next/navigation";
import {formatDate} from "@/lib/format";
import {groupAwardsByCategory, formatAwardMetric, rankClassName} from "@/lib/seasons";
import type {SeasonDetailResponse, DailyHighlight, RoundHighlight} from "@/types/seasons";
import {buildBreadcrumbJsonLd} from "@/lib/seo";
import "@/styles/components/season-detail.css";
import "@/styles/components/seasons.css";
import {Routes} from "../../../routes";

type PageProps = {
    params: {
        seasonNumber: string;
    };
};

const backend = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";
const numberFormatter = new Intl.NumberFormat();
const decimalFormatter = new Intl.NumberFormat(undefined, {maximumFractionDigits: 2});
const percentFormatter = new Intl.NumberFormat(undefined, {style: "percent", maximumFractionDigits: 1});

async function fetchSeasonDetail(seasonNumber: string): Promise<SeasonDetailResponse | null> {
    const res = await fetch(`${backend}/api/seasons/${encodeURIComponent(seasonNumber)}`, {
        headers: {"accept": "application/json"},
        next: {
            revalidate: 600,
            tags: [`season-detail-${seasonNumber}`]
        }
    });
    if (res.status === 404) {
        return null;
    }
    if (!res.ok) {
        throw new Error(`Failed to load season ${seasonNumber} (${res.status})`);
    }
    return res.json() as Promise<SeasonDetailResponse>;
}

export async function generateMetadata({params}: PageProps): Promise<Metadata> {
    const data = await fetchSeasonDetail(params.seasonNumber);
    const seasonLabel = data?.season?.seasonNumber ?? params.seasonNumber;
    const title = `Season #${seasonLabel} recap — Steam Review Guesser`;
    const description = data
        ? `Full recap, stats, and award winners from Steam Review Guesser Season #${data.season.seasonNumber}.`
        : `Detailed stats for Steam Review Guesser Season #${seasonLabel}.`;
    const canonical = typeof Routes.seasonDetail === "function"
        ? Routes.seasonDetail(seasonLabel)
        : `/review-guesser/season/${seasonLabel}`;

    return {
        title,
        description,
        keywords: [
            "recap",
            "statistics",
            "winners",
            "awards",
            "season history",
            "Steam Review Guesser",
            `Season ${seasonLabel}`
        ],
        alternates: {
            canonical
        },
        openGraph: {
            title,
            description,
            url: canonical,
            images: ["/opengraph-image"]
        },
        twitter: {
            card: "summary_large_image",
            title,
            description,
            images: ["/opengraph-image"]
        }
    };
}

export const revalidate = 600;

export default async function SeasonDetailPage({params}: PageProps) {
    const data = await fetchSeasonDetail(params.seasonNumber);
    if (!data) {
        notFound();
    }
    const {season, summary, topPlayers, highlights} = data;
    const progressPct = summary.durationDays > 0
        ? Math.round((summary.completedDays / summary.durationDays) * 100)
        : 0;
    const statusLabel = season.status === "FINALIZED"
        ? "Finalized"
        : season.status === "ACTIVE"
            ? "Active"
            : "Planned";
    const statsUpdated = summary.dataThrough ? formatDate(summary.dataThrough) : null;
    const breadcrumbJsonLd = buildBreadcrumbJsonLd([
        {name: "Home", url: Routes.home},
        {name: "Seasons", url: Routes.seasons},
        {name: `Season #${season.seasonNumber}`, url: Routes.seasonDetail(season.seasonNumber)},
    ]);

    return (
        <section className="container season-detail">
            <script type="application/ld+json" dangerouslySetInnerHTML={{
                __html: JSON.stringify(breadcrumbJsonLd)
            }} />
            <nav aria-label="Breadcrumb" className="season-detail__breadcrumbs">
                <Link href={Routes.seasons} className="season-detail__back-link">
                    ← Seasons overview
                </Link>
            </nav>

            <header className="season-detail__hero">
                <div className="season-detail__pill">Season #{season.seasonNumber}</div>
                <h1>Season #{season.seasonNumber} recap</h1>
                <p className="season-detail__subline">
                    {formatDate(season.startDate)} — {formatDate(season.endDate)} · <span>{statusLabel}</span>
                </p>
                <div className="season-detail__progress">
                    <div className="season-detail__progress-label">
                        {summary.completedDays} / {summary.durationDays} days recorded
                    </div>
                    <progress
                        className="season-detail__progress-bar"
                        value={Math.min(Math.max(progressPct, 0), 100)}
                        max={100}
                        aria-label="Season progress"
                    />
                </div>
                {season.status !== "FINALIZED" && (
                    <p className="season-detail__muted">
                        Awards finalize once the season closes. Live stats update every few minutes.
                    </p>
                )}
            </header>

            <section className="season-detail__section">
                <header className="season-detail__section-header">
                    <div>
                        <p className="season-detail__eyebrow">Season snapshot</p>
                        <h2>Performance overview</h2>
                    </div>
                    {statsUpdated && (
                        <p className="season-detail__muted">Updated through {statsUpdated}</p>
                    )}
                </header>
                {summary.totalPlayers === 0 ? (
                    <p className="season-detail__muted">No rounds have been recorded for this season yet.</p>
                ) : (
                    <div className="season-detail__stats-grid">
                        <StatsCard
                            label="Players"
                            value={numberFormatter.format(summary.totalPlayers)}
                            subline={`${summary.averageActiveDays.toFixed(1)} active days on avg`}
                        />
                        <StatsCard
                            label="Rounds played"
                            value={numberFormatter.format(summary.totalRounds)}
                            subline={`${numberFormatter.format(summary.totalPoints)} total points`}
                        />
                        <StatsCard
                            label="Points / round"
                            value={decimalFormatter.format(summary.averagePointsPerRound)}
                            subline={`${decimalFormatter.format(summary.averagePointsPerPlayer)} per player`}
                        />
                        <StatsCard
                            label="Hit rate"
                            value={percentFormatter.format(summary.hitRate)}
                            subline={`${numberFormatter.format(summary.totalHits)} correct buckets`}
                        />
                        <StatsCard
                            label="Longest streak"
                            value={`${summary.longestStreak || 0} ${summary.longestStreak === 1 ? "day" : "days"}`}
                            subline="Best uninterrupted run"
                        />
                        <StatsCard
                            label="Season progress"
                            value={`${Math.min(Math.max(progressPct, 0), 100)}%`}
                            subline={`${summary.completedDays} of ${summary.durationDays} days`}
                        />
                    </div>
                )}
            </section>

            {summary.totalPlayers > 0 && (
                <section className="season-detail__section">
                    <header className="season-detail__section-header">
                        <div>
                            <p className="season-detail__eyebrow">Daily highlights</p>
                            <h2>Standout challenge days</h2>
                        </div>
                        <p className="season-detail__muted">Links jump to the archive for each day.</p>
                    </header>
                    <div className="season-detail__highlights">
                        {renderHighlightCard(highlights?.highestAvg, "Highest average score", "Players crushed the picks this day.")}
                        {renderHighlightCard(highlights?.lowestAvg, "Toughest challenge", "Lowest average score across the season.")}
                        {renderHighlightCard(highlights?.busiest, "Most active day", "Highest number of unique players.")}
                        {renderRoundHighlightCard(highlights?.easiestRound, "Easiest round", "Highest average points in a single round.")}
                        {renderRoundHighlightCard(highlights?.hardestRound, "Hardest round", "Lowest average points in a single round.")}
                    </div>
                </section>
            )}

            <section className="season-detail__section">
                <header className="season-detail__section-header">
                    <div>
                        <p className="season-detail__eyebrow">Top performers</p>
                        <h2>Season leaderboard</h2>
                    </div>
                    <p className="season-detail__muted">Top 15 players ranked by total points.</p>
                </header>
                {topPlayers.length === 0 ? (
                    <p className="season-detail__muted">No player stats yet.</p>
                ) : (
                    <div className="season-detail__table-wrap">
                        <table className="season-detail__table">
                            <thead>
                            <tr>
                                <th scope="col" className="num">#</th>
                                <th scope="col">Player</th>
                                <th scope="col" className="num">Points</th>
                                <th scope="col" className="num">Rounds</th>
                                <th scope="col" className="num">Hits</th>
                                <th scope="col" className="num">Avg / round</th>
                                <th scope="col" className="num">Avg / day</th>
                                <th scope="col" className="num">Streak</th>
                            </tr>
                            </thead>
                            <tbody>
                            {topPlayers.map(player => (
                                <tr key={player.steamId}>
                                    <td className="num">{player.rank}</td>
                                    <td>
                                        <div className="season-detail__player">
                                            {player.avatar && (
                                                <div className="season-detail__player-avatar" aria-hidden="true">
                                                    <Image
                                                        src={player.avatar}
                                                        alt=""
                                                        width={32}
                                                        height={32}
                                                        placeholder={player.avatarBlurHash ? "blur" : "empty"}
                                                        blurDataURL={player.avatarBlurHash || undefined}
                                                    />
                                                </div>
                                            )}
                                            <Link href={`/profile/${encodeURIComponent(player.steamId)}`}
                                                  className="season-detail__player-link">
                                                {player.personaName}
                                            </Link>
                                        </div>
                                    </td>
                                    <td className="num">{numberFormatter.format(player.totalPoints)}</td>
                                    <td className="num">{numberFormatter.format(player.rounds)}</td>
                                    <td className="num">{numberFormatter.format(player.hits)}</td>
                                    <td className="num">{decimalFormatter.format(player.avgPointsPerRound)}</td>
                                    <td className="num">{decimalFormatter.format(player.avgPointsPerDay)}</td>
                                    <td className="num">{player.longestStreak}</td>
                                </tr>
                            ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>

            <section className="season-detail__section">
                <header className="season-detail__section-header">
                    <div>
                        <p className="season-detail__eyebrow">Season awards</p>
                        <h2>Category winners</h2>
                    </div>
                    <p className="season-detail__muted">Top placements per award category.</p>
                </header>
                {season.awards.length === 0 ? (
                    <p className="season-detail__muted">Awards will appear once the season is finalized.</p>
                ) : (
                    <div className="season-detail__awards-grid">
                        {groupAwardsByCategory(season.awards).map(group => (
                            <section className="season-card" key={group.category}>
                                <header className="season-card__header">
                                    <div>
                                        <p className="season-card__eyebrow">{group.label}</p>
                                        <h3>{group.label}</h3>
                                    </div>
                                </header>
                                <div className="season-card__awards">
                                    <section className="season-card__category">
                                        <ol className="season-card__placements">
                                            {group.awards.map(award => (
                                                <li className="season-card__placement"
                                                    key={`${group.category}-${award.steamId}-${award.placementLevel}`}>
                                                    <span className={rankClassName(award.placementLevel)}>
                                                        #{award.placementLevel}
                                                    </span>
                                                    <Link href={`/profile/${encodeURIComponent(award.steamId)}`}
                                                          className="season-card__player"
                                                          aria-label={`Open profile for ${award.personaName}`}>
                                                        <div className="season-card__avatar-wrap"
                                                             aria-hidden={award.avatar ? "false" : "true"}>
                                                            {award.avatar && (
                                                                <Image
                                                                    src={award.avatar}
                                                                    alt=""
                                                                    width={40}
                                                                    height={40}
                                                                    placeholder={award.avatarBlurHash ? "blur" : "empty"}
                                                                    blurDataURL={award.avatarBlurHash || undefined}
                                                                />
                                                            )}
                                                        </div>
                                                        <div>
                                                            <p className="season-card__name">{award.personaName}</p>
                                                            <p className="season-card__metric">
                                                                {formatAwardMetric(award)}
                                                            </p>
                                                        </div>
                                                    </Link>
                                                </li>
                                            ))}
                                        </ol>
                                    </section>
                                </div>
                            </section>
                        ))}
                    </div>
                )}
            </section>
        </section>
    );
}

function StatsCard({label, value, subline}: {label: string; value: string; subline?: string}) {
    return (
        <article className="season-detail__stat-card">
            <p className="season-detail__stat-label">{label}</p>
            <p className="season-detail__stat-value">{value}</p>
            {subline && <p className="season-detail__stat-subline">{subline}</p>}
        </article>
    );
}

function renderHighlightCard(highlight: DailyHighlight | null | undefined, title: string, description: string) {
    if (!highlight) {
        return (
            <article className="season-detail__highlight-card" key={title}>
                <p className="season-detail__muted">{title}</p>
                <p className="season-detail__stat-value">—</p>
                <p className="season-detail__muted">{description}</p>
            </article>
        );
    }
    const archiveLink = `${Routes.archive}/${highlight.date}`;
    return (
        <article className="season-detail__highlight-card" key={title}>
            <p className="season-detail__eyebrow">{title}</p>
            <h3>{formatDate(highlight.date)}</h3>
            <p className="season-detail__muted">{description}</p>
            <dl className="season-detail__highlight-metrics">
                <div>
                    <dt>Avg score</dt>
                    <dd>{decimalFormatter.format(highlight.avgScore)}</dd>
                </div>
                <div>
                    <dt>Players</dt>
                    <dd>{numberFormatter.format(highlight.playerCount)}</dd>
                </div>
            </dl>
            <Link href={archiveLink} className="season-detail__highlight-link">
                View archive →
            </Link>
        </article>
    );
}

function renderRoundHighlightCard(highlight: RoundHighlight | null | undefined, title: string, description: string) {
    if (!highlight) {
        return (
            <article className="season-detail__highlight-card" key={title}>
                <p className="season-detail__muted">{title}</p>
                <p className="season-detail__stat-value">—</p>
                <p className="season-detail__muted">{description}</p>
            </article>
        );
    }
    const archiveLink = `${Routes.archive}/${highlight.date}#round-${highlight.roundIndex}`;
    const appLabel = highlight.appName || `App ${highlight.appId}`;
    return (
        <article className="season-detail__highlight-card" key={title}>
            <p className="season-detail__eyebrow">{title}</p>
            <h3>{appLabel}</h3>
            <p className="season-detail__muted">{description}</p>
            <p className="season-detail__muted">{formatDate(highlight.date)} · Round {highlight.roundIndex}</p>
            <dl className="season-detail__highlight-metrics">
                <div>
                    <dt>Avg points</dt>
                    <dd>{decimalFormatter.format(highlight.avgScore)}</dd>
                </div>
                <div>
                    <dt>Players</dt>
                    <dd>{numberFormatter.format(highlight.playerCount)}</dd>
                </div>
            </dl>
            <Link href={archiveLink} className="season-detail__highlight-link">
                View archive →
            </Link>
        </article>
    );
}

