import type {Metadata} from "next";
import Image from "next/image";
import Link from "next/link";
import {formatDate} from "@/lib/format";
import {groupAwardsByCategory, formatAwardMetric, rankClassName} from "@/lib/seasons";
import type {SeasonView} from "@/types/seasons";
import "@/styles/components/seasons.css";
import {Routes} from "../../routes";

type CurrentSeasonResponse = {
    season: SeasonView;
    today: string;
    daysRemaining: number;
    nextSeasonStart: string;
};

const backend = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";

async function loadCurrentSeason(): Promise<CurrentSeasonResponse | null> {
    try {
        const res = await fetch(`${backend}/api/seasons/current`, {
            next: {revalidate: 300, tags: ["seasons-current"]}
        });
        if (!res.ok) return null;
        return await res.json() as CurrentSeasonResponse;
    } catch {
        return null;
    }
}

async function loadSeasons(): Promise<SeasonView[]> {
    try {
        const res = await fetch(`${backend}/api/seasons?limit=8`, {
            next: {revalidate: 600, tags: ["seasons-list"]}
        });
        if (!res.ok) return [];
        return await res.json() as SeasonView[];
    } catch {
        return [];
    }
}

export default async function SeasonsPage() {
    const [currentData, seasons] = await Promise.all([
        loadCurrentSeason(),
        loadSeasons()
    ]);

    if (!currentData) {
        return (
            <section className="container seasons">
                <header className="seasons__hero">
                    <h1>Seasons</h1>
                    <p className="seasons__intro">
                        Seasons are temporarily unavailable. Please try again soon.
                    </p>
                </header>
            </section>
        );
    }

    const previousSeasons = seasons.filter(season =>
        season.status === "FINALIZED" && season.id !== currentData.season.id
    );

    return (
        <section className="container seasons">
            <header className="seasons__hero">
                <Link href={`/review-guesser/seasons/${currentData.season.seasonNumber}`} className="seasons__pill">Season #{currentData.season.seasonNumber}</Link>
                <h1>Seasons</h1>
                <dl className="seasons__meta">
                    <div>
                        <dt>Runs</dt>
                        <dd>{formatDate(currentData.season.startDate)} — {formatDate(currentData.season.endDate)}</dd>
                    </div>
                    <div>
                        <dt>Days remaining</dt>
                        <dd>{currentData.daysRemaining}</dd>
                    </div>
                    <div>
                        <dt>Next awards</dt>
                        <dd>{formatDate(currentData.nextSeasonStart)}</dd>
                    </div>
                </dl>
                <p className="seasons__intro">
                    Seasons reset the playing field every few weeks so new challengers can climb to the top.
                    Awards are handed out the day after a season ends, then the next race begins.
                </p>
            </header>

            <section className="seasons__section" aria-labelledby="past-seasons-title">
                <div className="seasons__section-header">
                    <div>
                        <p className="seasons__eyebrow">Past seasons</p>
                        <h2 id="past-seasons-title">Award winners</h2>
                    </div>
                    <p className="seasons__muted">Top placements for each category per season.</p>
                </div>
                {previousSeasons.length === 0 ? (
                    <p className="seasons__muted">No completed seasons yet. Check back once the first season wraps up.</p>
                ) : (
                    <div className="seasons__list">
                        {previousSeasons.map(season => (
                            <article className="season-card" key={season.id}>
                                <header className="season-card__header">
                                    <div>
                                        <Link href={Routes.seasonDetail(season.seasonNumber)} className="season-card__eyebrow">Season #{season.seasonNumber}</Link>
                                        <h3>
                                            <Link href={Routes.seasonDetail(season.seasonNumber)}
                                                  className="season-card__title-link"
                                                  aria-label={`Open recap for season #${season.seasonNumber}`}>
                                                {formatDate(season.startDate)} – {formatDate(season.endDate)}
                                            </Link>
                                        </h3>
                                    </div>
                                </header>
                                <div className="season-card__awards">
                                    {groupAwardsByCategory(season.awards).map(group => (
                                        <section className="season-card__category" key={group.category}>
                                            <h4>{group.label}</h4>
                                            <ol className="season-card__placements">
                                                {group.awards.map(award => (
                                                    <li className="season-card__placement" key={`${group.category}-${award.steamId}-${award.placementLevel}`}>
                                                        <span className={rankClassName(award.placementLevel)}>#{award.placementLevel}</span>
                                                        <Link href={`/profile/${encodeURIComponent(award.steamId)}`}
                                                              className="season-card__player"
                                                              aria-label={`Open profile for ${award.personaName}`}>
                                                            <div className="season-card__avatar-wrap" aria-hidden={award.avatar ? "false" : "true"}>
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
                                    ))}
                                </div>
                                <div className="season-card__footer">
                                    <Link href={Routes.seasonDetail(season.seasonNumber)}
                                          className="season-card__link"
                                          aria-label={`View detailed recap for season #${season.seasonNumber}`}>
                                        View season recap →
                                    </Link>
                                </div>
                            </article>
                        ))}
                    </div>
                )}
            </section>
        </section>
    );
}

export const metadata: Metadata = {
    title: "Seasons — Steam Review Guesser",
    description: "Track the current season schedule and browse past Steam Review Guesser award winners.",
    alternates: {
        canonical: Routes.seasons
    },
    keywords: [
        "seasons",
        "awards",
        "winners",
        "Steam games",
        "competition",
        "schedule"
    ],
    openGraph: {
        title: "Steam Review Guesser Seasons",
        description: "Track the current season schedule and browse past award winners.",
        url: Routes.seasons,
        images: ["/opengraph-image"]
    },
    twitter: {
        card: "summary_large_image",
        title: "Steam Review Guesser Seasons",
        description: "Track the current season schedule and browse past award winners.",
        images: ["/opengraph-image"]
    }
};

export const revalidate = 300;
