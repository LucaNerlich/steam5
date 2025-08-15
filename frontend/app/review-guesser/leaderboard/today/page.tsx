export const dynamic = 'force-dynamic';

import "@/styles/components/leaderboard.css";

type LeaderEntry = { steamId: string; personaName: string; totalPoints: number; rounds: number };

async function loadLeaderboardToday(): Promise<LeaderEntry[]> {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const res = await fetch(`${backend}/api/leaderboard/today`, {
        cache: 'no-store',
        headers: {accept: 'application/json'}
    });
    if (!res.ok) throw new Error('Failed to load leaderboard');
    return res.json();
}

export default async function LeaderboardTodayPage() {
    const today = await loadLeaderboardToday();
    return (
        <section className="container">
            <h1>Review Guesser — Leaderboard</h1>
            <nav className="leaderboard__toggle" aria-label="Leaderboard view">
                <a href="/review-guesser/leaderboard" className="btn-ghost">All-time</a>
                <a href="/review-guesser/leaderboard/today" className="btn-ghost is-active">Today</a>
            </nav>
            <h2>Today</h2>
            <p className="text-muted">Today's total points by player (sorted highest first)</p>
            <div className="leaderboard">
                <table className="leaderboard__table" aria-label="Today Leaderboard">
                    <thead>
                    <tr>
                        <th scope="col" className="num">#</th>
                        <th scope="col">Player</th>
                        <th scope="col" className="num">Points</th>
                        <th scope="col" className="num">Rounds</th>
                    </tr>
                    </thead>
                    <tbody>
                    {today.map((e, i) => (
                        <tr key={`today-${e.steamId}`}>
                            <td>{i + 1}</td>
                            <td><strong>{e.personaName || e.steamId}</strong></td>
                            <td className="num">{e.totalPoints}</td>
                            <td className="num">{e.rounds}</td>
                        </tr>
                    ))}
                    </tbody>
                </table>
            </div>
        </section>
    );
}
