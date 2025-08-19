import Link from "next/link";
import LeaderboardTable, {LeaderEntry} from "@/components/LeaderboardTable";

// use LeaderEntry type from LeaderboardTable

async function loadLeaderboardToday(): Promise<LeaderEntry[]> {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const res = await fetch(`${backend}/api/leaderboard/today`, {
        headers: {accept: 'application/json'},
        next: {revalidate: 30},
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
                <Link href="/review-guesser/leaderboard" className="btn-ghost">All-time</Link>
                <Link href="/review-guesser/leaderboard/today" className="btn-ghost is-active">Today</Link>
            </nav>
            <h2>Today</h2>
            <p className="text-muted">Today&apos;s total points by player (sorted highest first)</p>
            <LeaderboardTable entries={today} ariaLabel="Today Leaderboard"/>
        </section>
    );
}
