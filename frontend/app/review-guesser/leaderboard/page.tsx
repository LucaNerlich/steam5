export const dynamic = 'force-dynamic';

type LeaderEntry = { steamId: string; personaName: string; totalPoints: number; rounds: number };

import "@/styles/components/leaderboard.css";

async function loadLeaderboard(): Promise<LeaderEntry[]> {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const res = await fetch(`${backend}/api/leaderboard/today`, {
        cache: 'no-store',
        headers: {accept: 'application/json'}
    });
    if (!res.ok) throw new Error('Failed to load leaderboard');
    return res.json();
}

export default async function LeaderboardPage() {
    const data = await loadLeaderboard();
    return (
        <section className="container">
            <h1>Review Guesser — Leaderboard</h1>
            <p className="text-muted">Today&apos;s total points by player (sorted highest first)</p>
            <div className="leaderboard">
                <table className="leaderboard__table" aria-label="Leaderboard">
                    <thead>
                    <tr>
                        <th scope="col" className="num">#</th>
                        <th scope="col">Player</th>
                        <th scope="col" className="num">Points</th>
                        <th scope="col" className="num">Rounds</th>
                    </tr>
                    </thead>
                    <tbody>
                    {data.map((e, i) => (
                        <tr key={e.steamId}>
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


