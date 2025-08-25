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

                <section aria-labelledby="days-title">
                    <h2 id="days-title">Review Guesser — Rounds by day</h2>
                    {data.days.length === 0 ? (
                        <p className="muted">No rounds yet.</p>
                    ) : (
                        data.days.map(day => (
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
                                                <td data-label="Selected">{r.selectedBucket}</td>
                                                <td data-label="Actual">{r.actualBucket}</td>
                                                <td data-label="Points" className="num">{r.points}</td>
                                            </tr>
                                        ))}
                                        </tbody>
                                    </table>
                                </div>
                            </article>
                        ))
                    )}
                </section>
            </section>
        </section>
    );
}


