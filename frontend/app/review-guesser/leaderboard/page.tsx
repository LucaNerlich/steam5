import Link from "next/link";
import "@/styles/components/leaderboard.css";

export const dynamic = 'force-dynamic';

type LeaderEntry = { steamId: string; personaName: string; totalPoints: number; rounds: number };

async function loadLeaderboardAll(): Promise<LeaderEntry[]> {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const res = await fetch(`${backend}/api/leaderboard/all`, {
        cache: 'no-store',
        headers: {accept: 'application/json'}
    });
    if (!res.ok) throw new Error('Failed to load leaderboard');
    return res.json();
}

export default async function LeaderboardPage() {
    const all = await loadLeaderboardAll();
    return (
        <section className="container">
            <h1>Review Guesser — Leaderboard</h1>
            <nav className="leaderboard__toggle" aria-label="Leaderboard view">
                <Link href="/review-guesser/leaderboard" className="btn-ghost is-active">All-time</Link>
                <Link href="/review-guesser/leaderboard/today" className="btn-ghost">Today</Link>
            </nav>
            <h2>All-time</h2>
            <p className="text-muted">Overall points summed across all days (sorted highest first)</p>
            <div className="leaderboard">
                <table className="leaderboard__table" aria-label="All-time Leaderboard">
                    <thead>
                    <tr>
                        <th scope="col" className="num">#</th>
                        <th scope="col">Player</th>
                        <th scope="col" className="num">Points</th>
                        <th scope="col" className="num">Rounds</th>
                    </tr>
                    </thead>
                    <tbody>
                    {all.map((e, i) => (
                        <tr key={`all-${e.steamId}`}>
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


