export const dynamic = 'force-dynamic';

type LeaderEntry = { steamId: string; personaName: string; totalPoints: number; rounds: number };

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
            <ol style={{paddingLeft: '1.25rem'}}>
                {data.map((e) => (
                    <li key={e.steamId} style={{marginBottom: '0.5rem'}}>
                        <strong>{e.personaName || e.steamId}</strong> — {e.totalPoints} pts ({e.rounds} rounds)
                    </li>
                ))}
            </ol>
        </section>
    );
}


