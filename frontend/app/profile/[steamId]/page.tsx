import Image from "next/image";
import {notFound} from "next/navigation";
import "@/styles/components/profile.css";

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
            selectedBucket: string;
            actualBucket: string;
            points: number;
        }>;
    }>;
};

export default async function ProfilePage({params}: { params: { steamId: string } }) {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const {steamId} = await params;
    const res = await fetch(`${backend}/api/profile/${encodeURIComponent(steamId)}`, {cache: 'no-store'});
    if (res.status === 404) return notFound();
    if (!res.ok) throw new Error(`Failed to load profile: ${res.status}`);
    const data = await res.json() as ProfileResponse;

    const name = data.personaName || data.steamId;

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
                                   width={64}
                                   height={64}/>
                        </div>
                    )}
                    <div className="profile__heading">
                        <h1 id="profile-title" className="profile__title">{name}</h1>
                        <div className="profile__meta">
                            <span className="text-muted">Steam5 — Review Guesser</span>
                            {data.profileUrl && (
                                <a href={data.profileUrl} target="_blank" rel="noopener noreferrer"
                                   className="profile__external">Open Steam profile</a>
                            )}
                        </div>
                    </div>
                </section>

                <section className="profile__stats" aria-labelledby="stats-title">
                    <h2 id="stats-title" className="sr-only">Overall stats</h2>
                    <dl className="stats-grid">
                        <div>
                            <dt>Points</dt>
                            <dd>{data.stats.totalPoints}</dd>
                        </div>
                        <div>
                            <dt>Rounds</dt>
                            <dd>{data.stats.rounds}</dd>
                        </div>
                        <div>
                            <dt>Hits</dt>
                            <dd>{data.stats.hits}</dd>
                        </div>
                        <div>
                            <dt>Too High</dt>
                            <dd>{data.stats.tooHigh}</dd>
                        </div>
                        <div>
                            <dt>Too Low</dt>
                            <dd>{data.stats.tooLow}</dd>
                        </div>
                        <div>
                            <dt>Avg</dt>
                            <dd>{data.stats.avgPoints.toFixed(2)}</dd>
                        </div>
                    </dl>
                </section>

                <section className="profile__days" aria-labelledby="days-title">
                    <h2 id="days-title">Review Guesser — Rounds by day</h2>
                    {data.days.length === 0 ? (
                        <p className="text-muted">No rounds yet.</p>
                    ) : (
                        data.days.map(day => (
                            <article key={day.date} className="profile__day">
                                <h3 className="profile__day-title">{day.date}</h3>
                                <table className="profile__rounds">
                                    <thead>
                                    <tr>
                                        <th scope="col">Round</th>
                                        <th scope="col">AppID</th>
                                        <th scope="col">Selected</th>
                                        <th scope="col">Actual</th>
                                        <th scope="col">Points</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    {day.rounds.map(r => (
                                        <tr key={r.roundIndex}>
                                            <td>#{r.roundIndex}</td>
                                            <td>{r.appId}</td>
                                            <td>{r.selectedBucket}</td>
                                            <td>{r.actualBucket}</td>
                                            <td>{r.points}</td>
                                        </tr>
                                    ))}
                                    </tbody>
                                </table>
                            </article>
                        ))
                    )}
                </section>
            </section>
        </section>
    );
}


