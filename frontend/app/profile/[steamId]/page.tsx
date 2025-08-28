import Image from "next/image";
import {notFound} from "next/navigation";
import type {Metadata} from "next";
import "@/styles/components/profile.css";
import PerformanceSection from "@/components/PerformanceSection";
import {Suspense} from "react";

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
    const description = `Scores and rounds for ${name} in Steam5 Review Guesser.`;
    const path = `/profile/${encodeURIComponent(steamId)}`;
    return {
        title,
        description,
        alternates: { canonical: path },
        openGraph: {
            title,
            description,
            url: path,
            images: ['/opengraph-image'],
        },
        twitter: {
            card: 'summary_large_image',
            title,
            description,
            images: ['/opengraph-image'],
        },
    };
}


