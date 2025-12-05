import type {Metadata} from "next";
import Image from "next/image";
import {formatDate} from "@/lib/format";
import "@/styles/components/seasons.css";

type AwardView = {
    category: string;
    categoryLabel: string;
    placementLevel: number;
    steamId: string;
    personaName: string;
    avatar?: string | null;
    avatarBlurHash?: string | null;
    metricValue: number;
    tiebreakRoll?: number | null;
};

type SeasonView = {
    id: number;
    seasonNumber: number;
    startDate: string;
    endDate: string;
    status: "PLANNED" | "ACTIVE" | "FINALIZED";
    awardsFinalizedAt?: string | null;
    awards: AwardView[];
};

type CurrentSeasonResponse = {
    season: SeasonView;
    today: string;
    daysRemaining: number;
    nextSeasonStart: string;
};

const backend = process.env.NEXT_PUBLIC_API_DOMAIN || "http://localhost:8080";
const metricLabels: Record<string, string> = {
    MOST_POINTS: "pts",
    MOST_HITS: "hits"
};

export default async function SeasonsPage() {
    const [currentRes, seasonsRes] = await Promise.all([
        fetch(`${backend}/api/seasons/current`, {next: {revalidate: 300, tags: ["seasons-current"]}}),
        fetch(`${backend}/api/seasons?limit=8`, {next: {revalidate: 600, tags: ["seasons-list"]}})
    ]);

    if (!currentRes.ok) throw new Error(`Failed to load current season (${currentRes.status})`);
    if (!seasonsRes.ok) throw new Error(`Failed to load seasons (${seasonsRes.status})`);

    const currentData = await currentRes.json() as CurrentSeasonResponse;
    const seasons = await seasonsRes.json() as SeasonView[];

    const previousSeasons = seasons.filter(season =>
        season.status === "FINALIZED" && season.id !== currentData.season.id
    );

    return (
        <section className="container seasons">
            <header className="seasons__hero">
                <div className="seasons__pill">Season #{currentData.season.seasonNumber}</div>
                <h1>Seasons</h1>
                <p className="seasons__intro">
                    Seasons reset the playing field every few weeks so new challengers can climb to the top.
                    Awards are handed out the day after a season ends, then the next race begins.
                </p>
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
                                        <p className="season-card__eyebrow">Season #{season.seasonNumber}</p>
                                        <h3>{formatDate(season.startDate)} – {formatDate(season.endDate)}</h3>
                                    </div>
                                    <p className="season-card__subtext">
                                        Awards sent {season.awardsFinalizedAt ? formatDate(season.awardsFinalizedAt) : "—"}
                                    </p>
                                </header>
                                <div className="season-card__awards">
                                    {groupAwardsByCategory(season.awards).map(group => (
                                        <section className="season-card__category" key={group.category}>
                                            <h4>{group.label}</h4>
                                            <ol className="season-card__placements">
                                                {group.awards.map(award => (
                                                    <li className="season-card__placement" key={`${group.category}-${award.steamId}-${award.placementLevel}`}>
                                                        <span className="season-card__rank">#{award.placementLevel}</span>
                                                        <div className="season-card__player">
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
                                                                    {new Intl.NumberFormat().format(award.metricValue)} {metricLabels[award.category] || ''}
                                                                </p>
                                                            </div>
                                                        </div>
                                                    </li>
                                                ))}
                                            </ol>
                                        </section>
                                    ))}
                                </div>
                            </article>
                        ))}
                    </div>
                )}
            </section>
        </section>
    );
}

type AwardGroup = {
    category: string;
    label: string;
    awards: AwardView[];
};

function groupAwardsByCategory(awards: AwardView[]): AwardGroup[] {
    const grouped: Record<string, AwardGroup> = {};
    for (const award of awards) {
        if (!grouped[award.category]) {
            grouped[award.category] = {
                category: award.category,
                label: award.categoryLabel,
                awards: []
            };
        }
        grouped[award.category].awards.push(award);
    }
    return Object.values(grouped);
}

export const metadata: Metadata = {
    title: "Seasons — Steam Review Guesser",
    description: "Track the current season schedule and browse past Steam Review Guesser award winners.",
    alternates: {
        canonical: "/seasons"
    },
    openGraph: {
        title: "Steam Review Guesser Seasons",
        description: "Track the current season schedule and browse past award winners.",
        url: "/seasons",
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


