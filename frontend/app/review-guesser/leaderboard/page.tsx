import Link from "next/link";
import LeaderboardTable, {LeaderEntry} from "@/components/LeaderboardTable";

async function loadLeaderboardAll(): Promise<LeaderEntry[]> {
    const backend = process.env.NEXT_PUBLIC_API_DOMAIN || 'http://localhost:8080';
    const res = await fetch(`${backend}/api/leaderboard/all`, {
        headers: {accept: 'application/json'},
        next: {revalidate: 60},
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
            <LeaderboardTable entries={all} ariaLabel="All-time Leaderboard"/>
        </section>
    );
}


