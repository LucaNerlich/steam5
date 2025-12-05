import Image from "next/image";
import {notFound} from "next/navigation";
import type {Metadata} from "next";
import "@/styles/components/profile.css";
import PerformanceSection from "@/components/PerformanceSection";
import {Suspense} from "react";
import {formatDate} from "@/lib/format";

type ProfileResponse = {
    steamId: string;
    personaName?: string | null;
    avatar?: string | null;
    avatarBlurdata?: string | null;
    profileUrl?: string | null;
    stats: {
        totalPoints: number;
        rounds: number;
        hits: number;
        tooHigh: number;
        tooLow: number;
        avgPoints: number;
    };
    days: Array<{
        date: string;
        rounds: Array<{
            roundIndex: number;
            appId: number;
            appName?: string;
            selectedBucket: string;
            actualBucket: string;
            points: number;
        }>;
    }>;
    awards: Array<{
        seasonId: number;
        seasonNumber: number;
        seasonStart: string;
        seasonEnd: string;
        category: string;
        categoryLabel: string;
        placementLevel: number;
        metricValue: number;
        tiebreakRoll?: number | null;
    }>;
};

export default async function ProfilePage({params}: { params: { steamId: string } }) {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const {steamId} = await params;
    const res = await fetch(`${backend}/api/profile/${encodeURIComponent(steamId)}`, { next: { revalidate: 300, tags: [
                `profile:${steamId}`
            ] } });
    if (res.status === 404) return notFound();
    if (!res.ok) throw new Error(`Failed to load profile: ${res.status}`);
    const data = await res.json() as ProfileResponse;

    const name = data.personaName || data.steamId;
    const allDays = (data.days || []);
    const todayStr = new Date().toISOString().slice(0, 10);
    const days = allDays.filter(d => d.date !== todayStr);

    const awards = data.awards ?? [];
    const awardSeasons = groupAwardsBySeason(awards);

    return (
        <section className="container">
            <section className="profile" aria-labelledby="profile-title">
                <section className="profile__info">
                    {data.avatar && (
                        <div className="profile__avatar-wrap">
                            <Image className="profile__avatar"
                                   src={data.avatar}
                                   alt=""
                                   placeholder={data.avatarBlurdata ? 'blur' : 'empty'}
                                   blurDataURL={data.avatarBlurdata || undefined}
                                   width={56}
                                   height={56}/>
                        </div>
                    )}
                    <div>
                        <h1 id="profile-title" className="profile__title">{name}</h1>
                        <div className="profile__meta">
                            {data.profileUrl && (
                                <a href={data.profileUrl} target="_blank" rel="noopener noreferrer"
                                   className="profile__external">Open Steam profile</a>
                            )}
                        </div>
                    </div>
                </section>

                <section aria-labelledby="stats-title">
                    <h2 id="stats-title" className="sr-only">Overall stats</h2>
                    <dl className="stats-grid">
                        <div><dt>Points</dt><dd>{data.stats.totalPoints}</dd></div>
                        <div><dt>Rounds</dt><dd>{data.stats.rounds}</dd></div>
                        <div><dt>Hits</dt><dd>{data.stats.hits}</dd></div>
                        <div><dt>Too High</dt><dd>{data.stats.tooHigh}</dd></div>
                        <div><dt>Too Low</dt><dd>{data.stats.tooLow}</dd></div>
                        <div><dt>Avg</dt><dd>{data.stats.avgPoints.toFixed(2)}</dd></div>
                    </dl>
                </section>

                <section aria-labelledby="awards-title">
                    <h2 id="awards-title">Season awards</h2>
                    {awardSeasons.length === 0 ? (
                        <p className="muted">No season awards yet.</p>
                    ) : (
                        <div className="profile__awards">
                            {awardSeasons.map(season => (
                                <article key={season.seasonId ?? season.seasonNumber} className="profile__award-card">
                                    <div className="profile__award-card-header">
                                        <h3>Season #{season.seasonNumber}</h3>
                                        <p className="profile__award-card-dates">
                                            {formatDate(season.seasonStart)} – {formatDate(season.seasonEnd)}
                                        </p>
                                    </div>
                                    <ul className="profile__award-list">
                                        {season.awards.map(award => (
                                            <li
                                                className="profile__award-row"
                                                key={`${season.seasonNumber}-${award.category}-${award.placementLevel}`}>
                                                <span className={`profile__award-badge ${placementBadgeClass(award.placementLevel)}`}>
                                                    {ordinal(award.placementLevel)}
                                                </span>
                                                <div className="profile__award-content">
                                                    <p className="profile__award-category">{award.categoryLabel}</p>
                                                    <p className="profile__award-metric">
                                                        {award.metricValue.toLocaleString()} {metricLabel(award.category)}
                                                    </p>
                                                </div>
                                            </li>
                                        ))}
                                    </ul>
                                </article>
                            ))}
                        </div>
                    )}
                </section>

                <Suspense fallback={<div style={{height: 160, background: 'var(--color-border)', borderRadius: 8}}/>}>
                    {/* PerformanceSection is client and renders quickly; Suspense ensures streaming */}
                    <PerformanceSection days={allDays} />
                </Suspense>

                <section aria-labelledby="days-title">
                    <h2 id="days-title">Review Guesser — Rounds by day</h2>
                    {days.length === 0 ? (
                        <p className="muted">No rounds yet.</p>
                    ) : (
                        days.map(day => (
                            <article key={day.date} className="profile__day">
                                <h3 className="profile__day-title">{day.date}</h3>
                                <div className="profile__table-wrap">
                                    <table className="profile__table">
                                        <thead>
                                        <tr>
                                            <th scope="col">Game</th>
                                            <th scope="col">Selected</th>
                                            <th scope="col">Actual</th>
                                            <th scope="col" className="num">Points</th>
                                        </tr>
                                        </thead>
                                        <tbody>
                                        {day.rounds.map(r => (
                                            <tr key={r.roundIndex}>
                                                <td data-label="Game" className="profile__game">
                                                    <a href={`https://store.steampowered.com/app/${r.appId}`}
                                                       target="_blank" rel="noopener noreferrer">
                                                        {r.appName || r.appId}
                                                    </a>
                                                </td>
                                                <td data-label="Selected"
                                                    className={r.selectedBucket === r.actualBucket ? 'is-correct' : undefined}>{r.selectedBucket}</td>
                                                <td data-label="Actual"
                                                    className={r.selectedBucket === r.actualBucket ? 'is-correct' : undefined}>{r.actualBucket}</td>
                                                <td data-label="Points" className="num">{r.points}</td>
                                            </tr>
                                        ))}
                                        </tbody>
                                    </table>
                                </div>
                                <div className="profile__mobile-list" aria-label={`Rounds for ${day.date}`}>
                                    {day.rounds.map(r => (
                                        <div className="profile__mobile-card" key={r.roundIndex}>
                                            <div className="profile__mobile-row">
                                                <span className="label">Game</span>
                                                <span className="value profile__game">
                                                    <a href={`https://store.steampowered.com/app/${r.appId}`} target="_blank" rel="noopener noreferrer">
                                                        {r.appName || r.appId}
                                                    </a>
                                                </span>
                                            </div>
                                            <div
                                                className={"profile__mobile-row" + (r.selectedBucket === r.actualBucket ? ' is-correct' : '')}>
                                                <span className="label">Selected</span>
                                                <span className="value">{r.selectedBucket}</span>
                                            </div>
                                            <div
                                                className={"profile__mobile-row" + (r.selectedBucket === r.actualBucket ? ' is-correct' : '')}>
                                                <span className="label">Actual</span>
                                                <span className="value">{r.actualBucket}</span>
                                            </div>
                                            <div className="profile__mobile-row">
                                                <span className="label">Points</span>
                                                <span className="value strong">{r.points}</span>
                                            </div>
                                        </div>
                                    ))}
                                </div>
                            </article>
                        ))
                    )}
                </section>
            </section>
        </section>
    );
}

type AwardSeason = {
    seasonId?: number;
    seasonNumber: number;
    seasonStart: string;
    seasonEnd: string;
    awards: ProfileResponse["awards"];
};

function groupAwardsBySeason(awards: ProfileResponse["awards"]): AwardSeason[] {
    if (!awards || awards.length === 0) return [];
    const grouped = new Map<number, AwardSeason>();
    awards.forEach(award => {
        const existing = grouped.get(award.seasonNumber);
        if (existing) {
            existing.awards.push(award);
        } else {
            grouped.set(award.seasonNumber, {
                seasonId: award.seasonId,
                seasonNumber: award.seasonNumber,
                seasonStart: award.seasonStart,
                seasonEnd: award.seasonEnd,
                awards: [award]
            });
        }
    });
    return Array.from(grouped.values()).sort((a, b) => b.seasonNumber - a.seasonNumber);
}

function ordinal(value: number): string {
    const mod100 = value % 100;
    if (mod100 >= 11 && mod100 <= 13) return `${value}th`;
    switch (value % 10) {
        case 1:
            return `${value}st`;
        case 2:
            return `${value}nd`;
        case 3:
            return `${value}rd`;
        default:
            return `${value}th`;
    }
}

function metricLabel(category: string): string {
    switch (category) {
        case "MOST_POINTS":
            return "pts";
        case "MOST_HITS":
            return "hits";
        default:
            return "";
    }
}

function placementBadgeClass(level: number): string {
    if (level === 1) return "profile__award-badge--gold";
    if (level === 2) return "profile__award-badge--silver";
    if (level === 3) return "profile__award-badge--bronze";
    return "profile__award-badge--neutral";
}

export const revalidate = 300;

export async function generateMetadata({params}: { params: { steamId: string } }): Promise<Metadata> {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const {steamId} = await params;
    let name: string = steamId;
    try {
        const res = await fetch(`${backend}/api/profile/${encodeURIComponent(steamId)}`, { next: { revalidate: 300 } });
        if (res.ok) {
            const data = await res.json() as ProfileResponse;
            name = data.personaName && data.personaName.trim() ? data.personaName : steamId;
        }
    } catch {
        // ignore
    }
    const title = `${name} — Profile`;
    const description = `Scores, rounds, and recent performance for ${name} in Steam5 Review Guesser.`;
    const path = `/profile/${encodeURIComponent(steamId)}`;
    const ogImage = `/opengraph-image?variant=profile&steamId=${encodeURIComponent(steamId)}`;
    return {
        title,
        description,
        keywords: [
            'Steam',
            'profile',
            'player stats',
            'review guessing game',
            name
        ],
        alternates: { canonical: path },
        openGraph: {
            title,
            description,
            url: path,
            images: [ogImage],
        },
        twitter: {
            card: 'summary_large_image',
            title,
            description,
            images: [ogImage],
        },
    };
}


